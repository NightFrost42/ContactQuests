#!/usr/bin/env python3
"""Run ContactQuests regression tests in new worlds, retaining every prior result."""
import argparse
import datetime as dt
import json
import hashlib
import locale
import os
from pathlib import Path
import re
import shlex
import shutil
import socket
import subprocess
import sys
import time
from network_checks import inspect_log

ROOT = Path(__file__).resolve().parents[2]
SCENARIOS = ("client-smoke.json", "client-parcel-autofill.json",
             "client-red-packet-autofill.json", "client-postcard-autofill.json")
FOCUSED_CLIENT_SCENARIOS = ("client-jade-mailbox.json", "client-autofill-lifecycle.json")
INTEGRATED_SERVER_UI_ACTIONS = frozenset(("ui_navigation", "mailbox_binding_ui", "additional_ui", "jade_mailbox", "autofill_lifecycle"))
INTEGRATED_SERVER_UI_CASE_PREFIXES = ("client.navigation.", "client.mailbox.binding_ui.", "client.additional_ui.", "client.jade.mailbox.", "client.autofill.lifecycle.")


def real_llm_settings():
    endpoint = os.environ.get("CQ_TEST_LLM_ENDPOINT", "").strip()
    model = os.environ.get("CQ_TEST_LLM_MODEL", "").strip()
    if bool(endpoint) != bool(model):
        raise ValueError("Set both CQ_TEST_LLM_ENDPOINT and CQ_TEST_LLM_MODEL for opt-in real-provider acceptance")
    if not endpoint:
        return None
    from urllib.parse import urlparse
    parsed = urlparse(endpoint)
    if parsed.scheme not in ("http", "https") or parsed.hostname not in ("127.0.0.1", "localhost", "::1") \
            or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("Real-provider acceptance requires a loopback endpoint without credentials or query parameters")
    if len(model) > 128 or any(char.isspace() for char in model):
        raise ValueError("Invalid real-provider test model")
    return {"endpoint": endpoint, "model": model, "reasoning": "low"}


def real_llm_phases(mode):
    # One real conversation per run, with its same-world restart. Other
    # environments retain the deterministic physical-mail acceptance matrix.
    if mode in ("suite", "multiplayer"):
        return ["client-multiplayer", "client-multiplayer-rejoin"]
    if mode == "no-kubejs":
        return ["client-no-kubejs", "client-no-kubejs-rejoin"]
    if mode == "client":
        return ["client-smoke", "client-rejoin"]
    return []


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {}


def derive_network_scenario(scenario):
    """Keep the smoke actions that can run against a separate server, in order."""
    steps = scenario["steps"]
    omitted = set()
    next_action = None
    for index in range(len(steps) - 1, -1, -1):
        step = steps[index]
        action = step.get("action")
        if action == "wait_ticks":
            continue
        if action in INTEGRATED_SERVER_UI_ACTIONS:
            omitted.add(index)
        elif action == "seed_autofill" and step.get("type") in ("parcel", "red_packet", "postcard") \
                and next_action is not None and next_action.get("action") == "ui_navigation" \
                and next_action.get("phase") == "prepare" and next_action.get("type") == step["type"]:
            # This seed only prepares the immediately following navigation case.
            # Other seeds and every wait/action remain part of the network scenario.
            omitted.add(index)
        next_action = step
    return ({**scenario, "steps": [step for index, step in enumerate(steps) if index not in omitted]},
            [{"index": index, "step": steps[index]} for index in sorted(omitted)])


def network_coverage_manifest(manifest):
    """Do not inherit integrated UI requirements via the multiplayer smoke alias."""
    return {**manifest, "scenarios": [
        {**entry, "phases": [phase for phase in entry.get("phases", [])
                             if phase not in ("client-smoke", "client-rejoin")]}
        if entry["id"].startswith(INTEGRATED_SERVER_UI_CASE_PREFIXES) else entry
        for entry in manifest["scenarios"]
    ]}


def windows_path(path):
    text = str(path)
    if os.name == "nt":
        return text
    result = subprocess.run(["wslpath", "-w", text], check=True, capture_output=True, text=True)
    return result.stdout.strip()


def default_client_platform():
    return "windows" if os.name == "nt" or "microsoft" in os.uname().release.lower() else "host"


def _prefer_java17(candidates):
    for candidate in sorted({str(p) for p in candidates if p}, reverse=True):
        path = Path(candidate)
        if not path.is_file():
            continue
        home = path.resolve().parents[1]
        release = home / "release"
        version = re.search(r'^JAVA_VERSION="(\d+)(?:[.\-+\"]|$)',
                            release.read_text(encoding="utf-8"), re.MULTILINE) if release.is_file() else None
        if (version and version[1] == "17") or (not version and
                re.search(r"(?:^|[^0-9])17(?:[._+-]|$)", home.name)):
            return str(path)
    return None


def _java_candidates(windows):
    executable = "java.exe" if windows else "java"
    candidates = []
    if os.environ.get("JAVA_HOME"):
        home = os.environ["JAVA_HOME"]
        if os.name != "nt":
            home = re.sub(r"^([A-Za-z]):[/\\]", lambda m: "/mnt/" + m[1].lower() + "/", home).replace("\\", "/")
        candidates.append(Path(home) / "bin" / executable)
    try:
        home_dir = Path.home()
    except RuntimeError:
        home_dir = None
    if home_dir:
        candidates += list(home_dir.glob(f".jdks/*/bin/{executable}"))
        candidates += list(home_dir.glob(f".gradle/jdks/*/*/bin/{executable}"))
    if windows:
        drive = Path("C:/") if os.name == "nt" else Path("/mnt/c")
        candidates += list((drive / "Users").glob("*/.jdks/*/bin/java.exe"))
        candidates += list((drive / "Users").glob("*/.gradle/jdks/*/*/bin/java.exe"))
        for vendor in ("Java", "Eclipse Adoptium", "Microsoft", "Amazon Corretto", "Zulu"):
            candidates += list((drive / "Program Files" / vendor).glob("*/bin/java.exe"))
    else:
        candidates += list(Path("/usr/lib/jvm").glob("*/bin/java"))
        candidates += list(Path("/opt").glob("jdk-17*/bin/java"))
    on_path = shutil.which(executable)
    if on_path:
        candidates.append(Path(on_path))
    return candidates


def java17_home():
    windows = os.name == "nt"
    compiler = "javac.exe" if windows else "javac"
    candidates = [p for p in _java_candidates(windows) if p.resolve().with_name(compiler).is_file()]
    java = _prefer_java17(candidates)
    return str(Path(java).resolve().parents[1]) if java else None


def windows_java(preferred=None):
    override = os.environ.get("CQ_WINDOWS_JAVA")
    candidates = [override] if override else [preferred, *_java_candidates(True)]
    java = _prefer_java17(p for p in candidates if p and Path(p).name.lower() == "java.exe")
    if not java:
        raise ValueError("Set CQ_WINDOWS_JAVA to a Windows JDK 17 bin/java.exe executable")
    return java


def record_needs_prepare(record):
    if not record:
        return True
    java = str(record.get("java", ""))
    sep = record.get("pathSeparator", "")
    if os.name == "nt":
        return sep == ":" or java.startswith("/")
    return sep == ";" or bool(re.match(r"^[A-Za-z]:[/\\]", java))


def prepare(mode, result_dir):
    executable = os.environ.get("CQ_GRADLE")
    if executable:
        command = [executable]
    else:
        command = [str(ROOT / "gradlew.bat")] if os.name == "nt" else ["bash", str(ROOT / "gradlew")]
    command += ["--no-daemon", "--no-configuration-cache", "--console=plain"]
    if os.environ.get("CQ_GRADLE_HOME"):
        command += ["--gradle-user-home", os.environ["CQ_GRADLE_HOME"]]
    if os.environ.get("CQ_GRADLE_INIT"):
        command += ["--init-script", os.environ["CQ_GRADLE_INIT"]]
    command += shlex.split(os.environ.get("CQ_GRADLE_ARGS", ""), posix=(os.name != "nt"))
    if mode == "compile":
        command += ["compileKotlin", "compileJava", "compileTestmodJava"]
    else:
        command += ["contactquestsExportServerLaunch"]
        if mode in ("client", "suite", "multiplayer", "no-kubejs"):
            command += ["contactquestsExportClientLaunch"]
        if mode in ("suite", "multiplayer", "no-kubejs"):
            command += ["contactquestsExportDedicatedLaunch"]
    env = os.environ.copy()
    home = java17_home()
    if home:
        env["JAVA_HOME"] = home
    return execute(command, ROOT, env, result_dir / "prepare.log", 2400)


def verify_runner(result_dir):
    gate = execute([sys.executable, str(ROOT / "tests/runner/test_report_gate.py")], ROOT, os.environ.copy(),
                   result_dir / "runner-validation.log", 60)
    coverage = execute([sys.executable, str(ROOT / "tests/report/test_code_coverage.py")], ROOT, os.environ.copy(),
                       result_dir / "coverage-validation.log", 60)
    return gate or coverage


def execute(command, cwd, env, log_path, timeout):
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            code = 124
    print(f"{log_path.stem}: exit={code}; {log_path}", flush=True)
    return code


def stage(game_dir, kubejs=True):
    # No recursive deletion. Refuse to reuse paths so old evidence cannot turn a run green.
    game_dir.mkdir(parents=True, exist_ok=False)
    (game_dir / "gameteststructures").mkdir()
    shutil.copy2(ROOT / "tests/scenarios/common/contactquestsgametests.empty.snbt",
                 game_dir / "gameteststructures")
    if kubejs:
        for side in ("server_scripts", "client_scripts"):
            shutil.copytree(ROOT / "tests/kubejs/1.20.1" / side, game_dir / "kubejs" / side)
    (game_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")


def read_text_with_fallback(path):
    raw = Path(path).read_bytes()
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError:
        return raw.decode(locale.getpreferredencoding(False), errors="replace")


def tokens_from_file(path):
    # ModDev emits one Java-escaped argument per line, with comments/empty lines.
    return shlex.split(read_text_with_fallback(Path(path)), comments=True, posix=True)


def expand_arguments(arguments):
    result = []
    for argument in arguments:
        if argument.startswith("@"):
            result.extend(tokens_from_file(Path(argument[1:])))
        else:
            result.append(argument)
    return result


def convert_paths(value, path_separator):
    # All WSL build artifacts must live on Windows-mounted disks for a native JVM.
    # Convert absolute path strings within JVM properties and classpath/mod-folder lists.
    value = re.sub(r"/mnt/([a-zA-Z])/", lambda m: m[1].upper() + ":/", value)
    if path_separator == ":":
        if value.startswith("-javaagent:"):
            # The agent option's colon is syntax, not a classpath separator.
            value = "-javaagent:" + value[len("-javaagent:"):]
        else:
            value = re.sub(r":(?=[A-Za-z]:[/\\]|[a-z0-9_]+%%)", ";", value)
    return value


def quote_java_arg(value):
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def stage_production_jar(record, game_dir, vm, cp, env):
    """Load the built jar through normal mod discovery, with no main classes fallback."""
    source = Path(record["productionJar"])
    if not source.is_file():
        raise ValueError("Build the production jar before using exported launch settings")
    separator = record["pathSeparator"]
    main_paths = set()

    def strip_main(value):
        entries = value.split(separator)
        main_paths.update(entry.split("%%", 1)[1] for entry in entries if entry.startswith("contactquests%%"))
        return separator.join(entry for entry in entries if not entry.startswith("contactquests%%"))

    if "MOD_CLASSES" in env:
        remaining = strip_main(env["MOD_CLASSES"])
        if remaining:
            env["MOD_CLASSES"] = remaining
        else:
            env.pop("MOD_CLASSES")
    filtered_vm = []
    for argument in vm:
        if argument.startswith("-Dfml.modFolders="):
            remaining = strip_main(argument.split("=", 1)[1])
            if remaining:
                filtered_vm.append("-Dfml.modFolders=" + remaining)
        else:
            filtered_vm.append(argument)
    if not main_paths:
        raise ValueError("Exported launch has no ContactQuests main folders")
    vm = filtered_vm
    cp = [entry for entry in cp if entry not in main_paths]
    target = game_dir / "mods" / source.name
    target.parent.mkdir(exist_ok=True)
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    if target.exists():
        if hashlib.sha256(target.read_bytes()).hexdigest() != digest:
            raise ValueError("The production jar changed between initial run and saved-world rejoin")
    else:
        shutil.copy2(source, target)
    vm += ["-Dcontactquests.test.expectedJar=" + str(target),
           "-Dcontactquests.test.expectedJarSha256=" + digest]
    return vm, cp


def is_optional_script_artifact(path):
    return bool(re.search(r"(?:^|[/\\])(?:kubejs-forge|rhino)-[^/\\]+\.jar$", str(path)))


def is_optional_viewer_artifact(path):
    return bool(re.search(r"(?:^|[/\\])(?:jei-\d|mezz_config)[^/\\]*\.jar$", str(path)))


def windows_lwjgl_boot_modules(vm, cp, separator):
    """Keep early-rendering classes out of the changing mod-discovery UnionFS."""
    modules = [path for path in cp
               if re.fullmatch(r"lwjgl(?:-[a-z]+)*-\d[^/\\]*\.jar", Path(path).name)]
    if not modules:
        raise ValueError("Windows client launch has no LWJGL modules")
    # Resolve one copy of each module, then require each binding's Windows native.
    modules = list({Path(path).name: path for path in modules}.values())
    names = {Path(path).name for path in modules}
    for name in names:
        if "-natives-" not in name and name.removesuffix(".jar") + "-natives-windows.jar" not in names:
            raise ValueError("Windows native is missing for " + name)
    module_paths = set(modules)
    lwjgl_names = names
    cp = [path for path in cp if Path(path).name not in lwjgl_names]
    vm = list(vm)
    for index, argument in enumerate(vm):
        if argument in ("-p", "--module-path"):
            vm[index + 1] = separator.join(dict.fromkeys([*vm[index + 1].split(separator), *modules]))
            break
        if argument.startswith("--module-path="):
            existing = argument.split("=", 1)[1].split(separator)
            vm[index] = "--module-path=" + separator.join(dict.fromkeys([*existing, *modules]))
            break
    else:
        vm += ["--module-path", separator.join(modules)]
    if "ALL-MODULE-PATH" not in vm:
        vm += ["--add-modules", "ALL-MODULE-PATH"]
    return vm, cp, module_paths


def disable_early_window_control(game_dir):
    """Let Window.<init> create the GLFW window so test mixins can prevent focus stealing."""
    fml_config = Path(game_dir) / "config" / "fml.toml"
    fml_config.parent.mkdir(parents=True, exist_ok=True)
    if fml_config.is_file():
        text = fml_config.read_text(encoding="utf-8", errors="replace")
        updated, count = re.subn(r"(?m)^(\s*earlyWindowControl\s*=\s*)true\b", r"\1false", text)
        if count == 0 and not re.search(r"(?m)^\s*earlyWindowControl\s*=", text):
            updated = text + ("" if text.endswith("\n") or not text else "\n") + "earlyWindowControl = false\n"
        if updated != text:
            fml_config.write_text(updated, encoding="utf-8")
    else:
        fml_config.write_text("earlyWindowControl = false\n", encoding="utf-8")


def prepare_launch(record, side, game_dir, result_dir, platform, scenario=None, snapshot=None, rejoin=False,
                   server_address=None, server_result=None, dedicated=False, no_kubejs=False, real_llm=False):
    windows = platform == "windows"
    scenario_path = None
    excluded_scenario_steps = []
    result_dir.mkdir(parents=True, exist_ok=True)
    vm = expand_arguments(record["jvmArgs"])
    program = expand_arguments(record["args"])
    # ForgeGradle exports the main class separately; older records may include it.
    main_class = program.pop(0) if program and not program[0].startswith("-") and "." in program[0] else record["mainClass"]
    env = os.environ.copy()
    env.pop("JAVA_TOOL_OPTIONS", None)
    env.pop("JDK_JAVA_OPTIONS", None)
    env.update({k: str(v) for k, v in record["environment"].items()})
    cp = list(record["classpath"])
    if record.get("productionJar"):
        vm, cp = stage_production_jar(record, game_dir, vm, cp, env)
    def excluded_optional(path):
        return no_kubejs and (is_optional_script_artifact(path) or is_optional_viewer_artifact(path))

    excluded = [path for path in cp if excluded_optional(path)]
    if no_kubejs:
        cp = [path for path in cp if not excluded_optional(path)]
        vm += ["-Dcontactquests.test.noKubejs=true", "-Dcontactquests.test.noJei=true"]
    boot_modules = set()
    if windows and side == "client" and record.get("windowsNatives"):
        cp = [p for p in cp if not re.search(r"-natives-(?:linux|macos|windows-(?:x86|arm64))", p)]
        cp += record["windowsNatives"]
        vm, cp, boot_modules = windows_lwjgl_boot_modules(vm, cp, record["pathSeparator"])
    if windows and side == "client" and record.get("windowsLaunchAgent"):
        access_agent = record["windowsLaunchAgent"]
        if not Path(access_agent).is_file():
            raise ValueError("Export the Windows launch access agent before --prepared")
        vm += ["-javaagent:" + access_agent]
    # Both launcher classpaths must exclude optional jars. Filtering only the
    # JVM classpath still leaves NeoForge discovering them through the legacy file.
    if no_kubejs or windows:
        for index, argument in enumerate(vm):
            if argument.startswith("-DlegacyClassPath.file="):
                entries = read_text_with_fallback(argument.split("=", 1)[1]).splitlines()
                if no_kubejs:
                    excluded += [path for path in entries if excluded_optional(path)]
                    entries = [path for path in entries if not excluded_optional(path)]
                if windows:
                    entries = [path for path in entries if not re.search(r"-natives-(?:linux|macos|windows-(?:x86|arm64))", path)]
                    entries = list(dict.fromkeys([*entries, *record.get("windowsNatives", [])]))
                    if boot_modules:
                        boot_names = {Path(path).name for path in boot_modules}
                        entries = [path for path in entries if Path(path).name not in boot_names]
                target = result_dir / "runtime-legacy-classpath.txt"
                target.write_text("\n".join(convert_paths(path, record["pathSeparator"]) if windows else path
                                             for path in entries) + "\n", encoding="utf-8")
                vm[index] = "-DlegacyClassPath.file=" + str(target)
    cp = list(dict.fromkeys(cp))
    runtime_game_dir = windows_path(game_dir) if windows else str(game_dir)
    # Always use the current fresh directory and the test namespace.
    vm = [x for x in vm if not x.startswith(("-Dforge.enabledGameTestNamespaces=", "-Dneoforge.enabledGameTestNamespaces="))]
    vm += ["-Dforge.enabledGameTestNamespaces=contactquests_test", "-Xmx3G",
           "-Dcontactquests.test.server=" + str(not server_address).lower(),
           "-Dcontactquests.test.serverResult=" + str(server_result or result_dir / "server-results.json"),
           "-Dcontactquests.test.gameTestResult=" + str(result_dir / "gametest-results.json")]
    if snapshot:
        vm += ["-Dcontactquests.test.snapshot=" + str(snapshot)]
    live_llm = real_llm_settings() if real_llm else None
    if live_llm:
        vm += ["-Dcontactquests.test.realLlm=true",
               "-Dcontactquests.test.llmEndpoint=" + live_llm["endpoint"],
               "-Dcontactquests.test.llmModel=" + live_llm["model"]]
    if rejoin:
        vm += ["-Dcontactquests.test.rejoin=true"]
    if dedicated:
        vm += ["-Dcontactquests.test.dedicated=true"]
    if windows and side == "client":
        vm += ["-Dcontactquests.test.expectedClientPlatform=windows"]
    if server_address:
        vm += ["-Dcontactquests.test.serverAddress=" + server_address]
    if record.get("coverageAgent"):
        coverage_agent = record["coverageAgent"]
        execution = str(result_dir / "coverage.exec")
        dump = str(result_dir / "coverage-classes")
        vm += ["-javaagent:" + coverage_agent + "=destfile=" + execution + ",append=false,"
               "includes=com.creeperyang.contactquests.*:dev.ftb.mods.ftbquests.quest.*:com.flechazo.contact.common.block.MailboxBlock*"
               ":com.flechazo.contact.common.tileentity.MailboxBlockEntity*:com.flechazo.contact.common.storage.PlayerMailboxData*,"
               "excludes=com.creeperyang.contactquests.test.*,classdumpdir=" + dump]
    if side == "client":
        disable_early_window_control(game_dir)
        scenario_path = ROOT / "tests/scenarios/common" / scenario
        if server_address:
            network_scenario, excluded_scenario_steps = derive_network_scenario(read_json(scenario_path))
            scenario_path = result_dir / "client-scenario.json"
            write_json(scenario_path, network_scenario)
        vm += ["-Dcontactquests.test.client=true",
               "-Dcontactquests.test.clientScenario=" + str(scenario_path),
               "-Dcontactquests.test.clientResult=" + str(result_dir / "client-results.json")]
        # The scenario opens the save after the client controller starts.
        if "--quickPlaySingleplayer" in program:
            index = program.index("--quickPlaySingleplayer")
            del program[index:index + 2]
    if "--gameDir" in program:
        program[program.index("--gameDir") + 1] = runtime_game_dir
    # With no --gameDir argument, launch() uses game_dir as Forge's workingDir.
    if windows:
        separator = ";"
        vm = [convert_paths(x, record["pathSeparator"]) for x in vm]
        vm = [x.replace("/", "\\") if x.startswith("-Dlog4j2.configurationFile=") else x for x in vm]
        program = [convert_paths(x, record["pathSeparator"]) for x in program]
        cp = [convert_paths(x, record["pathSeparator"]) for x in cp]
        env = {k: convert_paths(v, record["pathSeparator"]) if k in record["environment"] else v
               for k, v in env.items()}
        java = windows_java(record.get("java"))
    else:
        separator = record["pathSeparator"]
        java = record["java"]
    # Java argument files also avoid Windows' command length limit.
    arg_file = result_dir / "launch-arguments.txt"
    arg_file.write_text("\n".join(quote_java_arg(x) for x in [*vm, "-cp", separator.join(cp),
                                                            main_class, *program]) + "\n", encoding="utf-8")
    dependencies = dict(record["dependencies"])
    if no_kubejs:
        dependencies["kubejs"] = None
        dependencies["jei"] = None
    write_json(result_dir / "launch.json", {"platform": platform, "java": java,
                "side": side, "scenario": scenario, "dependencies": dependencies,
                "scenarioPath": str(scenario_path) if scenario_path else None,
                "excludedIntegratedServerSteps": excluded_scenario_steps,
                "noKubejs": no_kubejs, "excludedOptionalArtifacts": sorted(set(excluded)),
                 "windowsLwjglBootModules": sorted(boot_modules),
                "classpath": cp, "mainClass": main_class, "gameDirectory": runtime_game_dir})
    return [java, "@" + (windows_path(arg_file) if windows else str(arg_file))], env, result_dir / (side + ".log")


def launch(record, side, game_dir, result_dir, platform, timeout, scenario=None, snapshot=None, rejoin=False, **options):
    command, env, log = prepare_launch(record, side, game_dir, result_dir, platform, scenario, snapshot, rejoin, **options)
    return execute(command, game_dir, env, log, timeout)


def reserve_local_port(platform):
    if platform == "windows":
        # A port freed by WSL can remain reserved by Windows' WSL forwarding.
        # Probe using the native Windows JVM which will run the dedicated server.
        java = windows_java(read_json(ROOT / "build/contactquests-tests/runServer.json").get("java"))
        result = subprocess.run([java, windows_path(ROOT / "tests/runner/ProbePort.java")],
                                check=True, capture_output=True, text=True, timeout=30)
        return int(result.stdout.strip())
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def inspect_test_port(port, platform, result_dir):
    """Keep the actual listener and old test JVM evidence before it disappears."""
    if platform != "windows":
        command = ["ss", "-ltnp", "sport", "=", f":{port}"]
    else:
        powershell = os.environ.get("CQ_WINDOWS_POWERSHELL")
        if not powershell:
            if os.name == "nt":
                pwsh = Path("C:/Program Files/PowerShell/7/pwsh.exe")
                powershell = str(pwsh) if pwsh.is_file() else (shutil.which("pwsh") or shutil.which("powershell") or "powershell")
            else:
                powershell = "/mnt/c/Program Files/PowerShell/7/pwsh.exe"
        command = [powershell, "-NoLogo", "-NoProfile", "-File",
                   windows_path(ROOT / "tests/runner/inspect-test-ports.ps1"), "-Ports", str(port)]
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=30)
        write_json(result_dir / "port-inspection.json", {
            "port": port, "platform": platform, "exitCode": result.returncode,
            "stdout": result.stdout, "stderr": result.stderr,
        })
    except (OSError, subprocess.TimeoutExpired) as error:
        write_json(result_dir / "port-inspection.json", {
            "port": port, "platform": platform, "error": str(error),
        })


def stop_dedicated(process):
    if process.poll() is None:
        try:
            process.stdin.write("stop\n")
            process.stdin.flush()
            process.wait(timeout=45)
        except (BrokenPipeError, subprocess.TimeoutExpired):
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
    if process.stdin:
        process.stdin.close()
    return process.returncode


def dedicated_is_ready(server_results, log_path):
    # A cross-OS poll may race a replaced snapshot. Only readiness retries;
    # final result parsing and report gates remain strict after the JVM exits.
    try:
        return bool(read_json(server_results).get("loaded") and "Done (" in log_path.read_text(errors="replace"))
    except (json.JSONDecodeError, OSError):
        return False


def required_phase_cases(coverage, phase, server_data, client_data=None):
    """Check only this phase's raw evidence; preserve any duplicate failure."""
    required = {entry["id"] for entry in coverage["scenarios"]
                if entry.get("required") and phase in entry.get("phases", [])}
    observed = {case_id: case.get("success") is True
                for case_id, case in server_data.get("cases", {}).items()}
    for assertion in (client_data or {}).get("assertions", []):
        case_id = assertion.get("id")
        if case_id:
            observed[case_id] = observed.get(case_id, True) and assertion.get("success") is True
    return {"requiredCases": len(required),
            "missingCases": sorted(required - observed.keys()),
            "failedCases": sorted(case_id for case_id in required & observed.keys() if not observed[case_id])}


def run_multiplayer(client_record, dedicated_record, game_root, result_dir, platform, timeout, world_source,
                    no_kubejs=False, real_llm=False):
    if no_kubejs:
        no_kubejs_manifest = result_dir / "coverage-no-kubejs-manifest.json"
        frozen = no_kubejs_manifest.is_file()
        no_kubejs_coverage = read_json(no_kubejs_manifest if frozen else
                                     ROOT / "tests/scenarios/common/coverage-no-kubejs.json")
        if not no_kubejs_coverage.get("scenarios"):
            raise ValueError("Missing or empty no-KubeJS coverage manifest")
        # Suite coverage uses a different manifest; retain this exact gate too.
        if not frozen:
            write_json(no_kubejs_manifest, no_kubejs_coverage)
    server_dir = game_root / ("dedicated-no-kubejs" if no_kubejs else "dedicated-server")
    client_dir = game_root / ("no-kubejs-client" if no_kubejs else "multiplayer-client")
    stage(server_dir, kubejs=not no_kubejs)
    stage(client_dir, kubejs=not no_kubejs)
    if world_source:
        shutil.copytree(world_source, server_dir / "world")
    port = reserve_local_port(platform)
    # This test server accepts only local test clients and never exposes an
    # offline-mode server to the LAN or internet.
    (server_dir / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\n"
        "level-name=world\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\n"
        "gamemode=creative\nallow-flight=true\nmax-tick-time=120000\n", encoding="utf-8")
    snapshot = result_dir / ("no-kubejs-persistence-snapshot.json" if no_kubejs else "multiplayer-persistence-snapshot.json")
    phases = {}
    phase_names = ((False, "dedicated-no-kubejs", "client-no-kubejs"),
                   (True, "dedicated-no-kubejs-restart", "client-no-kubejs-rejoin")) if no_kubejs else (
                   (False, "dedicated-server", "client-multiplayer"),
                   (True, "dedicated-server-restart", "client-multiplayer-rejoin"))
    for rejoin, server_phase, client_phase in phase_names:
        server_results = result_dir / server_phase / "server-results.json"
        command, env, log_path = prepare_launch(dedicated_record, "server", server_dir, result_dir / server_phase,
                                              platform, snapshot=snapshot, rejoin=rejoin, dedicated=True,
                                              no_kubejs=no_kubejs, real_llm=real_llm)
        with log_path.open("w", encoding="utf-8") as log:
            process = subprocess.Popen(command, cwd=server_dir, env=env, stdin=subprocess.PIPE,
                                       stdout=log, stderr=subprocess.STDOUT, text=True)
            client_exit = None
            startup_error = ""
            try:
                deadline = time.monotonic() + min(timeout, 300)
                ready = False
                while time.monotonic() < deadline and process.poll() is None:
                    if dedicated_is_ready(server_results, log_path):
                        ready = True
                        break
                    time.sleep(1)
                if not ready:
                    raise RuntimeError(f"{server_phase} did not become ready; inspect {log_path}")
                scenario = ("client-no-kubejs-rejoin.json" if rejoin else "client-no-kubejs.json") if no_kubejs else (
                           "client-rejoin.json" if rejoin else "client-smoke.json")
                client_exit = launch(client_record, "client", client_dir, result_dir / client_phase, platform, timeout,
                                     scenario, snapshot, rejoin, server_address=f"127.0.0.1:{port}",
                                     server_result=server_results, dedicated=True, no_kubejs=no_kubejs, real_llm=real_llm)
            except Exception as error:
                startup_error = str(error)
                print(startup_error, file=sys.stderr, flush=True)
                inspect_test_port(port, platform, result_dir / server_phase)
            finally:
                server_exit = stop_dedicated(process)
                phases[server_phase] = {"exitCode": server_exit, "success": False}
        server_data = read_json(server_results)
        errors = inspect_log(log_path)
        server_ok = server_exit == 0 and server_data.get("loaded") and not server_data.get("failures") and not errors
        server_ok = server_ok and not startup_error
        if platform == "windows":
            server_ok = server_ok and str(server_data.get("runtime", {}).get("os", "")).startswith("Windows")
        if no_kubejs:
            server_gate = required_phase_cases(no_kubejs_coverage, server_phase, server_data)
            server_ok = server_ok and not server_gate["missingCases"] and not server_gate["failedCases"]
        phases[server_phase].update(success=bool(server_ok), networkErrors=errors[:10], pid=process.pid, error=startup_error)
        client_data = read_json(result_dir / client_phase / "client-results.json")
        client_errors = inspect_log(result_dir / client_phase / "client.log")
        client_ok = client_exit == 0 and client_data.get("success") and server_ok and not client_errors
        if not rejoin:
            client_ok = client_ok and server_data.get("success") and snapshot.is_file()
        if platform == "windows":
            client_ok = client_ok and str(client_data.get("runtime", {}).get("os", "")).startswith("Windows")
        if no_kubejs:
            client_gate = required_phase_cases(no_kubejs_coverage, client_phase, server_data, client_data)
            client_ok = client_ok and not client_gate["missingCases"] and not client_gate["failedCases"]
        phases[client_phase] = {"exitCode": client_exit, "success": bool(client_ok), "networkErrors": client_errors[:10],
                                "clientReportPresent": bool(client_data), "serverResult": server_results.relative_to(ROOT).as_posix()}
        if no_kubejs:
            for phase, gate in ((server_phase, server_gate), (client_phase, client_gate)):
                phases[phase].update(gate, coverageManifest=no_kubejs_manifest.relative_to(ROOT).as_posix())
                if gate["missingCases"] or gate["failedCases"]:
                    print(f"{phase}: required cases missing={gate['missingCases']}; failed={gate['failedCases']}",
                          flush=True)
        print(f"{client_phase}: {'PASS' if client_ok else 'FAIL'}; separate server exit={server_exit}", flush=True)
        if not client_ok:
            break
    return phases


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("compile", "server", "client", "suite", "multiplayer", "no-kubejs"))
    parser.add_argument("--scenario", choices=SCENARIOS + FOCUSED_CLIENT_SCENARIOS, default=SCENARIOS[0])
    parser.add_argument("--client-platform", choices=("host", "windows"), default=default_client_platform())
    parser.add_argument("--run-id", default=dt.datetime.now().strftime("%Y%m%d-%H%M%S-%f"))
    parser.add_argument("--prepared", action="store_true", help="Use freshly exported Gradle launch records")
    parser.add_argument("--timeout", type=int, default=900)
    args = parser.parse_args()
    try:
        live_llm = real_llm_settings()
    except ValueError as error:
        parser.error(str(error))
    if not re.fullmatch(r"[A-Za-z0-9_-]+", args.run_id):
        parser.error("run-id must contain only letters, digits, hyphens and underscores")
    result_dir = ROOT / "test-results" / args.run_id
    game_root = ROOT / ("run-test-" + args.run_id)
    if result_dir.exists() or game_root.exists():
        parser.error("This run already exists; use a new run-id to retain its evidence")
    result_dir.mkdir(parents=True)
    game_root.mkdir()
    write_json(result_dir / "source-manifest.json", {
        path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for language in ("java", "kotlin")
        for path in sorted((ROOT / "src/main" / language).rglob("*"))
        if path.is_file() and path.suffix in (".java", ".kt")
    })
    # Each historical result must retain the requirements used for that run.
    # Adding a new test tomorrow must not silently rewrite yesterday's verdict.
    manifest = "coverage-no-kubejs.json" if args.mode == "no-kubejs" else "coverage.json"
    shutil.copy2(ROOT / "tests/scenarios/common" / manifest, result_dir / "coverage-manifest.json")
    if args.mode == "multiplayer":
        # Suite mode still requires these cases from its actual integrated client.
        # Keep all explicit network phases, required flags and other case entries.
        write_json(result_dir / "coverage-manifest.json",
                   network_coverage_manifest(read_json(result_dir / "coverage-manifest.json")))
    if live_llm:
        frozen = read_json(result_dir / "coverage-manifest.json")
        frozen["realProvider"] = live_llm
        phases = real_llm_phases(args.mode)
        for entry in read_json(ROOT / "tests/scenarios/common/coverage-live-llm.json")["scenarios"]:
            selected = [phase for phase in phases if phase.endswith("rejoin") == entry["id"].endswith(("rejoin", "restart", "restored"))]
            if selected:
                frozen["scenarios"].append({**entry, "phases": selected})
        write_json(result_dir / "coverage-manifest.json", frozen)
    metadata = {"mode": args.mode, "clientPlatform": args.client_platform, "runId": args.run_id,
                "scenario": args.scenario.removesuffix(".json"),
                 "windowsFontBootLayer": False,
                "realProvider": live_llm,
                "realProviderPhases": real_llm_phases(args.mode) if live_llm else [],
                "escrowAudit": args.mode == "suite" or (args.mode == "client" and args.scenario == "client-smoke.json"),
                "startedAt": dt.datetime.now(dt.timezone.utc).isoformat(), "phases": {}}
    write_json(result_dir / "run.json", metadata)
    write_json(ROOT / "test-results/latest.json", {"runId": args.run_id})
    code = 0
    try:
        code = verify_runner(result_dir)
        metadata["runnerValidationExit"] = code
        needed_records = []
        if args.mode not in ("compile", "no-kubejs"):
            needed_records.append("runGameTestServer.json")
        if args.mode in ("client", "suite", "multiplayer", "no-kubejs"):
            needed_records.append("runClient.json")
        if args.mode in ("suite", "multiplayer", "no-kubejs"):
            needed_records.append("runServer.json")
        stale_prepared = any(record_needs_prepare(read_json(ROOT / "build/contactquests-tests" / name))
                             for name in needed_records)
        if not args.prepared or stale_prepared:
            if not code:
                code = prepare(args.mode, result_dir)
                metadata["prepareExit"] = code
        if not code and args.mode == "no-kubejs":
            client_record = read_json(ROOT / "build/contactquests-tests/runClient.json")
            dedicated_record = read_json(ROOT / "build/contactquests-tests/runServer.json")
            if not client_record or not dedicated_record:
                raise ValueError("Export client and dedicated server launch records before --prepared")
            artifact = Path(dedicated_record["productionJar"])
            metadata["productionJar"] = {"path": str(artifact), "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest()}
            metadata["withoutKubejs"] = True
            write_json(result_dir / "run.json", metadata)
            phases = run_multiplayer(client_record, dedicated_record, game_root, result_dir, args.client_platform,
                                     args.timeout, None, no_kubejs=True, real_llm=bool(live_llm))
            metadata["phases"].update(phases)
            if len(phases) != 4 or any(not phase["success"] for phase in phases.values()):
                code = 1
        if not code and args.mode not in ("compile", "no-kubejs"):
            server_record = read_json(ROOT / "build/contactquests-tests/runGameTestServer.json")
            if not server_record:
                raise ValueError("Run contactquestsExportServerLaunch before --prepared")
            if server_record.get("productionJar"):
                artifact = Path(server_record["productionJar"])
                metadata["productionJar"] = {"path": str(artifact),
                    "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest()}
                write_json(result_dir / "run.json", metadata)
            game_dir = game_root / "server"
            stage(game_dir)
            code = launch(server_record, "server", game_dir, result_dir / "server", "host", args.timeout)
            metadata["phases"]["server"] = {"exitCode": code}
            game_tests = read_json(result_dir / "server/gametest-results.json")
            server_data = read_json(result_dir / "server/server-results.json")
            if not game_tests.get("finished") or not game_tests.get("cases") or any(
                    not case.get("success") for case in game_tests.get("cases", {}).values()):
                code = 1
            expected = {entry["id"].split(".")[-1].lower()
                        for entry in read_json(result_dir / "coverage-manifest.json").get("scenarios", [])
                        if entry["id"].startswith("gametest.") and entry.get("required")}
            actual = {name.rsplit(".", 1)[-1].lower() for name in game_tests.get("cases", {})}
            missing = sorted(expected - actual)
            metadata["phases"]["server"].update(expectedTests=len(expected), observedTests=len(actual), missingTests=missing)
            if missing:
                code = 1
            if not server_data.get("loaded") or server_data.get("failures"):
                code = 1
            server_errors = inspect_log(result_dir / "server/server.log")
            if server_errors:
                code = 1
            metadata["phases"]["server"].update(success=code == 0, networkErrors=server_errors[:10])
            # A failing business assertion need not suppress independent client cases;
            # a startup crash without a usable world does.
            if args.mode in ("client", "suite") and (game_dir / "world/level.dat").is_file():
                client_record = read_json(ROOT / "build/contactquests-tests/runClient.json")
                if not client_record:
                    raise ValueError("Run contactquestsExportClientLaunch before --prepared")
                for scenario in SCENARIOS if args.mode == "suite" else [args.scenario]:
                    name = scenario.removesuffix(".json")
                    client_dir = game_root / name
                    stage(client_dir)
                    shutil.copytree(game_dir / "world", client_dir / "saves/ContactQuestsTest")
                    result = result_dir / name
                    snapshot = result_dir / "persistence-snapshot.json"
                    exit_code = launch(client_record, "client", client_dir, result,
                                       args.client_platform, args.timeout, scenario, snapshot=snapshot,
                                       real_llm=bool(live_llm) and args.mode == "client" and name == "client-smoke")
                    client_data = read_json(result / "client-results.json")
                    server_data = read_json(result / "server-results.json")
                    ok = exit_code == 0 and client_data.get("success") and server_data.get("loaded") \
                        and not server_data.get("failures")
                    if scenario == SCENARIOS[0]:
                        ok = ok and server_data.get("success")
                    if args.client_platform == "windows":
                        ok = ok and str(client_data.get("runtime", {}).get("os", "")).startswith("Windows")
                    errors = inspect_log(result / "client.log")
                    ok = ok and not errors
                    metadata["phases"][name] = {"exitCode": exit_code, "success": bool(ok), "networkErrors": errors[:10],
                                               "clientReportPresent": bool(client_data)}
                    if not ok:
                        code = 1
                    write_json(result_dir / "run.json", metadata)
                    if name == "client-smoke" and ok and snapshot.is_file():
                        # The first JVM has shut down and released the save lock.
                        # Open exactly the same save in a fresh process. No copy,
                        # staging or fixture rewrite is allowed in this phase.
                        second_result = result_dir / "client-rejoin"
                        rejoin_exit = launch(client_record, "client", client_dir, second_result,
                                             args.client_platform, args.timeout, "client-rejoin.json",
                                             snapshot=snapshot, rejoin=True, real_llm=bool(live_llm) and args.mode == "client")
                        rejoin_client = read_json(second_result / "client-results.json")
                        rejoin_server = read_json(second_result / "server-results.json")
                        rejoin_ok = rejoin_exit == 0 and rejoin_client.get("success") and rejoin_server.get("loaded") \
                            and not rejoin_server.get("failures")
                        if args.client_platform == "windows":
                            rejoin_ok = rejoin_ok and str(rejoin_client.get("runtime", {}).get("os", "")).startswith("Windows")
                        errors = inspect_log(second_result / "client.log")
                        rejoin_ok = rejoin_ok and not errors
                        metadata["phases"]["client-rejoin"] = {"exitCode": rejoin_exit, "success": bool(rejoin_ok),
                                                                "networkErrors": errors[:10], "clientReportPresent": bool(rejoin_client)}
                        if not rejoin_ok:
                            code = 1
                        write_json(result_dir / "run.json", metadata)
                        if rejoin_ok:
                            # Reopen the unchanged save once more after the actual reward claim.
                            # This third JVM must observe persisted emptiness, never reseed escrow.
                            audit_result = result_dir / "client-escrow-rejoin"
                            audit_exit = launch(client_record, "client", client_dir, audit_result,
                                                args.client_platform, args.timeout, "client-escrow-rejoin.json",
                                                snapshot=snapshot, rejoin=True)
                            audit_client = read_json(audit_result / "client-results.json")
                            audit_server = read_json(audit_result / "server-results.json")
                            audit_case = audit_server.get("cases", {}).get("server.features.escrow_audit", {})
                            audit_errors = inspect_log(audit_result / "client.log")
                            audit_ok = audit_exit == 0 and audit_client.get("success") and audit_server.get("loaded") \
                                and not audit_server.get("failures") and audit_case.get("success") and not audit_errors
                            if args.client_platform == "windows":
                                audit_ok = audit_ok and str(audit_client.get("runtime", {}).get("os", "")).startswith("Windows")
                            metadata["phases"]["client-escrow-rejoin"] = {
                                "exitCode": audit_exit, "success": bool(audit_ok), "networkErrors": audit_errors[:10],
                                "clientReportPresent": bool(audit_client)}
                            if not audit_ok:
                                code = 1
                            write_json(result_dir / "run.json", metadata)
            elif args.mode in ("client", "suite"):
                metadata["error"] = "Server did not produce a usable test world; client cases were not run"
                code = 1
            if args.mode in ("suite", "multiplayer") and (game_dir / "world/level.dat").is_file():
                client_record = read_json(ROOT / "build/contactquests-tests/runClient.json")
                dedicated_record = read_json(ROOT / "build/contactquests-tests/runServer.json")
                if not client_record or not dedicated_record:
                    raise ValueError("Export both client and dedicated server launch records before --prepared")
                phases = run_multiplayer(client_record, dedicated_record, game_root, result_dir, args.client_platform,
                                         args.timeout, game_dir / "world", real_llm=bool(live_llm))
                metadata["phases"].update(phases)
                if len(phases) != 4 or any(not phase["success"] for phase in phases.values()):
                    code = 1
                write_json(result_dir / "run.json", metadata)
                if args.mode == "suite":
                    optional_phases = run_multiplayer(client_record, dedicated_record, game_root, result_dir,
                                                     args.client_platform, args.timeout, None, no_kubejs=True)
                    metadata["phases"].update(optional_phases)
                    if len(optional_phases) != 4 or any(not phase["success"] for phase in optional_phases.values()):
                        code = 1
                    write_json(result_dir / "run.json", metadata)
    except Exception as error:
        metadata["error"] = str(error)
        print(str(error), file=sys.stderr, flush=True)
        code = 1
    metadata.update(executionExit=code, exitCode=code, finishedAt=dt.datetime.now(dt.timezone.utc).isoformat())
    write_json(result_dir / "run.json", metadata)
    if args.mode != "compile":
        source_freeze = result_dir / "source-manifest.json"
        coverage_check = subprocess.run([sys.executable, str(ROOT / "tests/report/code-coverage.py"),
                                         "--run-id", args.run_id, "--source-manifest", str(source_freeze)], cwd=ROOT, check=False)
        metadata["coverageExit"] = coverage_check.returncode
        if coverage_check.returncode:
            code = 1
            metadata["executionExit"] = code
        write_json(result_dir / "run.json", metadata)
    report_check = subprocess.run([sys.executable, str(ROOT / "tests/report/generate-report.py"),
                                  "--run-id", args.run_id, "--check"], cwd=ROOT, check=False)
    if report_check.returncode:
        code = 1
    metadata.update(exitCode=code, reportExit=report_check.returncode)
    write_json(result_dir / "run.json", metadata)
    return code


if __name__ == "__main__":
    sys.exit(main())
