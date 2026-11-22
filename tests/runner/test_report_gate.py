"""Guard against reporting a disconnected or incomplete client as passing."""
import copy
import importlib.util
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1] / "report/generate-report.py"
spec = importlib.util.spec_from_file_location("cq_report_gate", MODULE)
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)

RUNNER_MODULE = Path(__file__).resolve().parent / "run-tests.py"
runner_spec = importlib.util.spec_from_file_location("cq_test_runner", RUNNER_MODULE)
runner = importlib.util.module_from_spec(runner_spec)
runner_spec.loader.exec_module(runner)


class ForgeLaunchTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.game = self.root / "game"
        self.game.mkdir()
        self.jar = self.root / "contactquests-2.1.6-1.20.1-dev.jar"
        self.jar.write_bytes(b"fixed-production-jar")

    def java(self, name, version=None, windows=False):
        executable = self.root / name / "bin" / ("java.exe" if windows else "java")
        executable.parent.mkdir(parents=True)
        executable.touch()
        executable.with_name("javac.exe" if windows else "javac").touch()
        if version:
            (executable.parents[1] / "release").write_text('JAVA_VERSION="' + version + '"\n', encoding="utf-8")
        return executable

    def test_java17_selection_uses_release_and_never_falls_back_to_java21(self):
        newer = self.java("z-jdk-21", "21.0.5", windows=True)
        java = self.java("vendor-current", "17.0.13", windows=True)
        misleading = self.java("jdk-17-misnamed", "21.0.5", windows=True)
        named = self.java("jdk-17.0.12", windows=True)
        self.assertEqual(str(java), runner._prefer_java17([newer, java, misleading]))
        self.assertEqual(str(named), runner._prefer_java17([newer, named]))
        self.assertIsNone(runner._prefer_java17([newer, misleading]))
        with patch.dict(runner.os.environ, {}, clear=True), patch.object(runner, "_java_candidates", return_value=[java, newer]):
            self.assertEqual(str(java), runner.windows_java(str(newer)))
        with patch.dict(runner.os.environ, {}, clear=True), patch.object(runner, "_java_candidates", return_value=[]):
            self.assertEqual(str(java), runner.windows_java(str(java)))
            with self.assertRaisesRegex(ValueError, "JDK 17"):
                runner.windows_java(str(newer))

    def test_java_home_discovery_requires_a_jdk(self):
        windows = os.name == "nt"
        java = self.java("custom-home", "17.0.13", windows=windows)
        with patch.dict(runner.os.environ, {"JAVA_HOME": str(java.parents[1])}, clear=True), \
                patch.object(runner.Path, "glob", return_value=[]), patch.object(runner.shutil, "which", return_value=None):
            self.assertEqual(str(java.parents[1]), runner.java17_home())
            java.with_name("javac.exe" if windows else "javac").unlink()
            self.assertIsNone(runner.java17_home())

    def test_prepare_selects_wrapper_java_home_and_compile_or_export_tasks_without_execution(self):
        for name in ("posix", "nt"):
            for mode, tasks in (("compile", ["compileKotlin", "compileJava", "compileTestmodJava"]),
                                ("suite", ["contactquestsExportServerLaunch", "contactquestsExportClientLaunch",
                                           "contactquestsExportDedicatedLaunch"])):
                with self.subTest(platform=name, mode=mode), \
                        patch.object(runner, "os", SimpleNamespace(name=name, environ={"JAVA_HOME": "jdk-21"})), \
                        patch.object(runner, "java17_home", return_value="jdk-17"), \
                        patch.object(runner, "execute", return_value=0) as execute:
                    self.assertEqual(0, runner.prepare(mode, self.root))
                    command, cwd, env, _, _ = execute.call_args.args
                    wrapper = [str(runner.ROOT / "gradlew.bat")] if name == "nt" else ["bash", str(runner.ROOT / "gradlew")]
                    self.assertEqual(wrapper, command[:len(wrapper)])
                    self.assertEqual(tasks, command[-len(tasks):])
                    self.assertEqual("jdk-17", env["JAVA_HOME"])
                    self.assertEqual(runner.ROOT, cwd)

    def test_production_jar_replaces_all_main_paths_but_retains_testmod(self):
        for separator, base in ((":", "/build/"), (";", "C:/build/")):
            for source in ("env", "property", "both"):
                with self.subTest(separator=separator, source=source):
                    main, resources, test = [base + part for part in ("classes/main", "resources/main", "classes/testmod")]
                    entries = separator.join(["contactquests%%" + main, "contactquests%%" + resources, "contactquests_test%%" + test])
                    env = {"MOD_CLASSES": entries} if source != "property" else {}
                    vm = ["-Dfml.modFolders=" + entries] if source != "env" else []
                    record = {"productionJar": str(self.jar), "pathSeparator": separator}
                    vm, cp = runner.stage_production_jar(record, self.game, vm, [main, resources, test, "dependency.jar"], env)
                    self.assertEqual([test, "dependency.jar"], cp)
                    if source != "property":
                        self.assertEqual("contactquests_test%%" + test, env["MOD_CLASSES"])
                    if source != "env":
                        self.assertIn("-Dfml.modFolders=contactquests_test%%" + test, vm)
                    target = self.game / "mods" / self.jar.name
                    self.assertEqual(self.jar.read_bytes(), target.read_bytes())
                    self.assertIn("-Dcontactquests.test.expectedJar=" + str(target), vm)
                    self.assertIn("-Dcontactquests.test.expectedJarSha256=" + hashlib.sha256(self.jar.read_bytes()).hexdigest(), vm)

    def test_production_jar_requires_main_folders_and_rejects_changed_jar_on_rejoin(self):
        record = {"productionJar": str(self.jar), "pathSeparator": ":"}
        with self.assertRaisesRegex(ValueError, "main folders"):
            runner.stage_production_jar(record, self.game, [], [], {})
        env = {"MOD_CLASSES": "contactquests%%/main"}
        vm, cp = runner.stage_production_jar(record, self.game, ["-Dfml.modFolders=contactquests%%/main"], ["/main"], env)
        self.assertNotIn("MOD_CLASSES", env)
        self.assertFalse(any(arg.startswith("-Dfml.modFolders=") for arg in vm))
        self.assertEqual([], cp)
        self.jar.write_bytes(b"changed-production-jar")
        with self.assertRaisesRegex(ValueError, "changed between"):
            runner.stage_production_jar(record, self.game, [], ["/main"], {"MOD_CLASSES": "contactquests%%/main"})

    def test_forge_launch_without_boot_modules_preserves_args_and_filters_mods(self):
        for args in ([], ["--launchTarget", "forgeclient", "--gameDir", "stale"],
                     ["cpw.mods.bootstraplauncher.BootstrapLauncher", "--launchTarget", "forgeclient"],
                     ["--version", "1.20.1"], ["nogui"]):
            with self.subTest(args=args):
                record = {"java": "jdk-17/bin/java.exe", "mainClass": "cpw.mods.bootstraplauncher.BootstrapLauncher",
                          "jvmArgs": ["-Dforge.enabledGameTestNamespaces=old", "-Dneoforge.enabledGameTestNamespaces=old"],
                          "args": args, "classpath": ["/main", "/testmod", "/cache/kubejs-forge-2001.jar",
                          "/cache/rhino-forge-2001.jar", "/cache/jei-1.20.1-forge-15.jar", "/cache/mezz_config-1.jar"],
                          "environment": {"MOD_CLASSES": "contactquests%%/main:contactquests_test%%/testmod"},
                          "productionJar": str(self.jar), "pathSeparator": ":", "dependencies": {"forge": "47"}}
                before = copy.deepcopy(record)
                with patch.object(runner, "windows_java", return_value=record["java"]), \
                        patch.object(runner, "windows_path", side_effect=str), \
                        patch.object(runner, "windows_lwjgl_boot_modules") as boot, \
                        patch.object(runner, "execute", return_value=0) as execute:
                    result = self.root / "result"
                    self.assertEqual(0, runner.launch(record, "client", self.game, result, "windows", 1,
                                                     scenario="client-smoke.json", no_kubejs=True))
                    self.assertEqual(self.game, execute.call_args.args[1])
                    self.assertEqual("contactquests_test%%/testmod", execute.call_args.args[2]["MOD_CLASSES"])
                    boot.assert_not_called()
                tokens = runner.tokens_from_file(result / "launch-arguments.txt")
                self.assertEqual(1, tokens.count("-Dforge.enabledGameTestNamespaces=contactquests_test"))
                self.assertFalse(any("=old" in token or token.startswith("-javaagent:") for token in tokens))
                remaining = tokens[tokens.index(record["mainClass"]) + 1:]
                expected = list(args[1:] if args and args[0] == record["mainClass"] else args)
                if "--gameDir" in expected:
                    expected[expected.index("--gameDir") + 1] = str(self.game)
                self.assertEqual(expected, remaining)
                self.assertEqual(["/testmod"], runner.read_json(result / "launch.json")["classpath"])
                self.assertEqual(before, record)


class NetworkScenarioTests(unittest.TestCase):
    def test_network_omits_integrated_ui_but_keeps_real_delivery_and_reload(self):
        actions = [
            {"action": "additional_ui", "type": "config"},
            {"action": "wait_ticks", "ticks": 5},
            {"action": "additional_ui", "type": "required_tags"},
            {"action": "additional_ui", "type": "unlock_tags"},
            {"action": "jade_mailbox", "phase": "all"},
            {"action": "delivery_boundary", "type": "ender_postcard"},
            {"action": "native_reload"},
            {"action": "postcard_letters_ui", "rejoin": True},
        ]
        original = {"id": "added-ui-and-delivery", "steps": actions}
        derived, omitted = runner.derive_network_scenario(original)
        self.assertEqual([actions[i] for i in [1, 5, 6, 7]], derived["steps"])
        self.assertEqual([0, 2, 3, 4], [s["index"] for s in omitted])
        manifest = {"scenarios": [
            {"id": "client.additional_ui.required_tags.verify_saved", "phases": ["client-smoke"]},
            {"id": "server.delivery_boundary.ender_postcard.verify", "phases": ["client-smoke"]},
            {"id": "client.jade.mailbox.native_overlay", "phases": ["client-smoke", "client-jade-mailbox"]},
        ]}
        result = runner.network_coverage_manifest(manifest)
        self.assertEqual([], result["scenarios"][0]["phases"])
        self.assertEqual(["client-smoke"], result["scenarios"][1]["phases"])
        self.assertEqual(["client-jade-mailbox"], result["scenarios"][2]["phases"])
        self.assertEqual(["client-smoke"], manifest["scenarios"][0]["phases"])

    def test_network_filter_preserves_exact_step_order_and_metadata(self):
        original = {"id": "network-filter-fixture", "metadata": {"keep": True}, "steps": [
            {"action": "install_synthetic_tasks"},
            {"action": "seed_autofill", "type": "parcel"},
            {"action": "wait_ticks", "ticks": 30},
            {"action": "ui_navigation", "type": "parcel", "phase": "prepare", "variant": "back"},
            {"action": "wait_ticks", "ticks": 5},
            {"action": "ui_navigation", "type": "parcel", "phase": "open", "variant": "back"},
            {"action": "mailbox_binding_ui", "variant": "lower", "phase": "prepare"},
            {"action": "wait_ticks", "ticks": 80},
            {"action": "mailbox_binding_ui", "phase": "verify_restored"},
            {"action": "open_real_postbox"},
            {"action": "seed_mail", "type": "parcel"},
            {"action": "move_mail_to_postbox", "type": "parcel"},
            {"action": "assert_server_case", "id": "server.mail.actual"},
            {"action": "features_ui", "phase": "visible_request"},
            {"action": "llm_live", "phase": "publish", "requires_real_llm": True},
            {"action": "finish"},
        ]}
        before = copy.deepcopy(original)
        derived, omitted = runner.derive_network_scenario(original)
        kept = (0, 2, 4, 7, 9, 10, 11, 12, 13, 14, 15)
        removed = (1, 3, 5, 6, 8)
        self.assertEqual({**before, "steps": [before["steps"][i] for i in kept]}, derived)
        self.assertEqual([{"index": i, "step": before["steps"][i]} for i in removed], omitted)
        self.assertEqual(before, original)

    def test_network_filter_does_not_remove_other_seeds(self):
        cases = (
            ({"action": "seed_autofill", "type": "parcel"},
             [{"action": "open_autofill", "type": "parcel"}]),
            ({"action": "seed_autofill", "type": "red_packet"},
             [{"action": "assert_server_case", "id": "keep.seed.consumer"},
              {"action": "ui_navigation", "type": "red_packet", "phase": "prepare"}]),
            ({"action": "seed_autofill", "type": "parcel"},
             [{"action": "ui_navigation", "type": "red_packet", "phase": "prepare"}]),
            ({"action": "seed_autofill", "type": "postcard_instant"},
             [{"action": "ui_navigation", "type": "postcard", "phase": "prepare"}]),
            ({"action": "seed_autofill", "type": "postcard"},
             [{"action": "ui_navigation", "type": "postcard", "phase": "open"}]),
            ({"action": "seed_mail", "type": "parcel"},
             [{"action": "mailbox_binding_ui", "phase": "prepare"}]),
            ({"action": "seed_autofill", "type": "parcel"}, []),
        )
        for seed, following in cases:
            with self.subTest(seed=seed, following=following):
                waiting = {"action": "wait_ticks", "ticks": 30}
                original = {"id": "other-seed-fixture", "steps": [seed, waiting, *following]}
                before = copy.deepcopy(original)
                derived, omitted = runner.derive_network_scenario(original)
                expected = [seed, waiting, *[step for step in following
                            if step["action"] not in ("ui_navigation", "mailbox_binding_ui")]]
                self.assertEqual(expected, derived["steps"])
                self.assertTrue(all(entry["step"]["action"] in ("ui_navigation", "mailbox_binding_ui")
                                    for entry in omitted))
                self.assertEqual(before, original)


class ReadinessEvidenceTests(unittest.TestCase):
    def test_optional_viewer_removal_is_scoped_to_real_jei_jar(self):
        for path in ["/cache/jei-1.20.1-forge-15.20.0.106.jar",
                     "C:\\cache\\jei-1.20.1-forge-15.20.0.106.jar",
                     "/cache/mezz_config-0.6.2+1.20.1-forge.jar", "C:\\cache\\mezz_config-0.6.2.jar"]:
            self.assertTrue(runner.is_optional_viewer_artifact(path))
        for path in ["/jei-cache/ftb-library-forge-2001.2.12.jar", "/mods/contactquests-2.1.6.jar",
                     "/mods/my-jei-addon-1.0.jar", "/mods/jei-settings.json"]:
            self.assertFalse(runner.is_optional_viewer_artifact(path))

    def test_optional_script_removal_matches_forge_kubejs_and_rhino(self):
        for path in ("/cache/kubejs-forge-2001.6.5-build.16.jar",
                     "C:\\cache\\kubejs-forge-2001.6.5-build.16.jar",
                     "/cache/rhino-forge-2001.2.3-build.10.jar", "/cache/rhino-2001.2.3.jar"):
            self.assertTrue(runner.is_optional_script_artifact(path), path)
        for path in ("/cache/kubejs-addon-1.0.jar", "/rhino-cache/architectury-forge-9.2.14.jar",
                     "/cache/kubejs-forge.json", "/cache/my-rhino-1.0.jar"):
            self.assertFalse(runner.is_optional_script_artifact(path), path)

    def test_live_conversation_has_one_explicit_network_path_and_restart(self):
        self.assertEqual(["client-multiplayer", "client-multiplayer-rejoin"], runner.real_llm_phases("suite"))
        self.assertEqual(runner.real_llm_phases("suite"), runner.real_llm_phases("multiplayer"))
        self.assertEqual(["client-no-kubejs", "client-no-kubejs-rejoin"], runner.real_llm_phases("no-kubejs"))
        self.assertEqual(["client-smoke", "client-rejoin"], runner.real_llm_phases("client"))
        self.assertEqual([], runner.real_llm_phases("server"))

    def test_real_provider_is_explicit_and_preserves_loopback_url(self):
        with patch.dict(runner.os.environ, {}, clear=True):
            self.assertIsNone(runner.real_llm_settings())
            runner.os.environ["CQ_TEST_LLM_ENDPOINT"] = "http://127.0.0.1:3425/v1"
            with self.assertRaises(ValueError): runner.real_llm_settings()
            runner.os.environ["CQ_TEST_LLM_MODEL"] = "auto/deepseek-v4.1-flash"
            settings = runner.real_llm_settings()
            self.assertEqual("http://127.0.0.1:3425/v1", settings["endpoint"])
            self.assertEqual(settings["endpoint"], runner.convert_paths(settings["endpoint"], ":"))
            for invalid in ["http://user:secret@127.0.0.1/v1", "http://external.invalid/v1", "http://127.0.0.1/v1?token=secret"]:
                runner.os.environ["CQ_TEST_LLM_ENDPOINT"] = invalid
                with self.assertRaises(ValueError): runner.real_llm_settings()

    def test_live_partial_snapshot_waits_but_final_parsing_stays_strict(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence = root / "server-results.json"
            log = root / "server.log"
            log.write_text("Done (1.0s)!\n", encoding="utf-8")
            evidence.write_text('{"loaded":true}', encoding="utf-8")
            self.assertTrue(runner.dedicated_is_ready(evidence, log))
            for partial in ['{"loaded":', '{"loaded":true}trailing']:
                evidence.write_text(partial, encoding="utf-8")
                self.assertFalse(runner.dedicated_is_ready(evidence, log))
                with self.assertRaises(json.JSONDecodeError):
                    runner.read_json(evidence)
            evidence.write_text('{"loaded":true}', encoding="utf-8")
            self.assertTrue(runner.dedicated_is_ready(evidence, log))

    def test_readiness_requires_both_evidence_and_actual_server_started_log(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence = root / "server-results.json"
            log = root / "server.log"
            log.write_text("Starting\n", encoding="utf-8")
            self.assertFalse(runner.dedicated_is_ready(evidence, log))
            evidence.write_text('{"loaded":true}', encoding="utf-8")
            self.assertFalse(runner.dedicated_is_ready(evidence, log))
            log.write_text("Done (1.0s)!\n", encoding="utf-8")
            evidence.write_text('{"loaded":false}', encoding="utf-8")
            self.assertFalse(runner.dedicated_is_ready(evidence, log))


class NoKubejsPhaseGateTests(unittest.TestCase):
    PHASES = (("dedicated-no-kubejs", "client-no-kubejs"),
              ("dedicated-no-kubejs-restart", "client-no-kubejs-rejoin"))

    def setUp(self):
        self.coverage = runner.read_json(runner.ROOT / "tests/scenarios/common/coverage-no-kubejs.json")

    def required(self, phase):
        return {entry["id"] for entry in self.coverage["scenarios"]
                if entry.get("required") and phase in entry.get("phases", [])}

    def evidence(self, phase):
        # Isolated Python fixtures, never runtime/game acceptance evidence.
        entries = [entry for entry in self.coverage["scenarios"]
                   if entry.get("required") and phase in entry.get("phases", [])]
        server = {"loaded": True, "success": True, "cases": {
            entry["id"]: {"success": True} for entry in entries if entry["scope"] == "server"}}
        client = {"success": True, "assertions": [
            {"id": entry["id"], "success": True} for entry in entries if entry["scope"] == "client"]}
        return server, client

    def test_exact_manifest_phases_exclude_gametest_and_optional_cases(self):
        for phases in self.PHASES:
            for phase in phases:
                with self.subTest(phase=phase):
                    coverage = copy.deepcopy(self.coverage)
                    coverage["scenarios"].append({"id": "fixture.optional", "required": False, "phases": [phase]})
                    server, client = self.evidence(phase)
                    server["cases"]["fixture.optional"] = {"success": False}
                    result = runner.required_phase_cases(coverage, phase, server, client)
                    self.assertEqual({"requiredCases": len(self.required(phase)),
                                      "missingCases": [], "failedCases": []}, result)
                    self.assertTrue(self.required(phase))
                    self.assertFalse(any(case_id.startswith("gametest.") for case_id in self.required(phase)))
        self.assertIn("server.commands.tags_remove_no_team_completion", self.required("client-no-kubejs"))
        self.assertIn("client.postcard.layout.received", self.required("client-no-kubejs"))
        self.assertIn("server.features.escrow_claim", self.required("client-no-kubejs-rejoin"))

    def test_first_and_rejoin_cannot_supply_each_others_evidence(self):
        first, rejoin = "client-no-kubejs", "client-no-kubejs-rejoin"
        for source, target in ((first, rejoin), (rejoin, first)):
            with self.subTest(source=source, target=target):
                server, client = self.evidence(source)
                result = runner.required_phase_cases(self.coverage, target, server, client)
                self.assertEqual(sorted(self.required(target) - self.required(source)), result["missingCases"])
                self.assertTrue(result["missingCases"])

    def test_required_flag_and_failure_survive_duplicate_pass(self):
        phase = "client-no-kubejs"
        server, client = self.evidence(phase)
        server_id = "server.commands.tags_remove_no_team_completion"
        client_id = "client.postcard.layout.received"
        coverage = copy.deepcopy(self.coverage)
        coverage["scenarios"].append({"id": server_id, "required": False, "phases": [phase]})
        server["cases"][server_id] = {"success": False}
        client["assertions"] += [{"id": server_id, "success": True, "required": False},
                                 {"id": client_id, "success": False},
                                 {"id": client_id, "success": True, "required": False}]
        before = copy.deepcopy(coverage)
        result = runner.required_phase_cases(coverage, phase, server, client)
        self.assertEqual([], result["missingCases"])
        self.assertEqual(sorted((server_id, client_id)), result["failedCases"])
        self.assertEqual(len(self.required(phase)), result["requiredCases"])
        self.assertEqual(before, coverage)

    def run_network_fixture(self, change=None, client_echo=None, frozen_coverage=None, no_kubejs=True):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            results = root / "test-results/fixture"
            runner.write_json(root / "tests/scenarios/common/coverage-no-kubejs.json",
                              self.coverage if frozen_coverage is None and no_kubejs else {"scenarios": []})
            frozen_path = results / "coverage-no-kubejs-manifest.json"
            if frozen_coverage is not None:
                runner.write_json(frozen_path, frozen_coverage)
                frozen_bytes = frozen_path.read_bytes()
            runner.write_json(results / ("no-kubejs-persistence-snapshot.json" if no_kubejs else
                                         "multiplayer-persistence-snapshot.json"), {})
            other_server, other_client = {"cases": {}}, {"success": True, "assertions": []}
            for server_phase, client_phase in self.PHASES:
                server, client = self.evidence(client_phase)
                server["cases"].update(self.evidence(server_phase)[0]["cases"])
                if not no_kubejs:
                    server_phase = server_phase.replace("no-kubejs", "server")
                    client_phase = client_phase.replace("no-kubejs", "multiplayer")
                # Keep the other environment's same-ID PASS even if no-KubeJS loses/fails it.
                other_server["cases"].update(copy.deepcopy(server["cases"]))
                other_client["assertions"].extend(copy.deepcopy(client["assertions"]))
                if client_echo and client_echo[0] == client_phase:
                    client["assertions"].append({"id": client_echo[1], "success": True})
                for phase, data, name in ((server_phase, server, "server"), (client_phase, client, "client")):
                    if change and change[0] == phase:
                        _, case_id, fail = change
                        if name == "server":
                            if fail:
                                data["cases"][case_id]["success"] = False
                            else:
                                del data["cases"][case_id]
                        else:
                            for assertion in data["assertions"]:
                                if assertion["id"] == case_id:
                                    assertion["success"] = False
                            if not fail:
                                data["assertions"] = [a for a in data["assertions"] if a["id"] != case_id]
                    runner.write_json(results / phase / (name + "-results.json"), data)
                    (results / phase / (name + ".log")).write_text("normal startup\n", encoding="utf-8")
            for other in ("client-smoke", "client-multiplayer"):
                runner.write_json(results / other / "server-results.json", other_server)
                runner.write_json(results / other / "client-results.json", other_client)
            with (patch.object(runner, "ROOT", root),
                  patch.object(runner, "stage", side_effect=lambda path, **kw: path.mkdir(parents=True, exist_ok=True)),
                  patch.object(runner, "reserve_local_port", return_value=12345),
                  patch.object(runner, "prepare_launch", side_effect=lambda *a, **kw: ([], {}, a[3] / "server.log")),
                  patch.object(runner.subprocess, "Popen") as process,
                  patch.object(runner, "dedicated_is_ready", return_value=True),
                  patch.object(runner, "launch", return_value=0),
                  patch.object(runner, "stop_dedicated", return_value=0),
                  patch.object(runner.time, "sleep", side_effect=AssertionError("No waits in gate tests")),
                  patch("builtins.print") as printed):
                process.return_value.poll.return_value = None
                process.return_value.pid = 123
                phases = runner.run_multiplayer({}, {}, root / "game", results, "host", 1, None, no_kubejs=no_kubejs)
            if no_kubejs:
                self.assertEqual(self.coverage if frozen_coverage is None else frozen_coverage,
                                 runner.read_json(frozen_path))
                if frozen_coverage is not None:
                    self.assertEqual(frozen_bytes, frozen_path.read_bytes())
                for result in phases.values():
                    self.assertEqual("test-results/fixture/coverage-no-kubejs-manifest.json", result["coverageManifest"])
            else:
                self.assertFalse(frozen_path.exists())
            return phases, [call.args[0] for call in printed.call_args_list]

    def test_run_rejects_missing_or_failed_case_despite_other_environment_pass(self):
        for source, case_id in (("dedicated-no-kubejs", "server.commands.tags_remove_no_team_completion"),
                                ("client-no-kubejs", "client.postcard.layout.received")):
            for fail in (False, True):
                with self.subTest(source=source, case_id=case_id, fail=fail):
                    phases, output = self.run_network_fixture((source, case_id, fail))
                    gate = phases["client-no-kubejs"]
                    self.assertFalse(gate["success"])
                    self.assertEqual([case_id], gate["failedCases" if fail else "missingCases"])
                    self.assertEqual([], gate["missingCases" if fail else "failedCases"])
                    self.assertNotIn("client-no-kubejs-rejoin", phases)
                    self.assertTrue(any(case_id in line and "missing=" in line and "failed=" in line for line in output))

    def test_rejoin_rechecks_shared_cases_in_its_own_process(self):
        for phase, case_id in (("dedicated-no-kubejs-restart", "server.no_kubejs.dependencies_absent"),
                               ("client-no-kubejs-rejoin", "client.no_kubejs.dependencies_absent")):
            for fail in (False, True):
                with self.subTest(phase=phase, fail=fail):
                    phases, _ = self.run_network_fixture((phase, case_id, fail))
                    self.assertTrue(phases["client-no-kubejs"]["success"])
                    self.assertFalse(phases[phase]["success"])
                    self.assertFalse(phases["client-no-kubejs-rejoin"]["success"])
                    self.assertEqual([case_id], phases[phase]["failedCases" if fail else "missingCases"])

    def test_server_cannot_borrow_its_required_case_from_client(self):
        case_id = "server.no_kubejs.dependencies_absent"
        phases, _ = self.run_network_fixture(("dedicated-no-kubejs", case_id, False),
                                             client_echo=("client-no-kubejs", case_id))
        self.assertEqual([case_id], phases["dedicated-no-kubejs"]["missingCases"])
        self.assertFalse(phases["dedicated-no-kubejs"]["success"])
        self.assertFalse(phases["client-no-kubejs"]["success"])

    def test_phase_failure_remains_required_in_report_despite_repeated_pass(self):
        phases, _ = self.run_network_fixture(("client-no-kubejs", "client.postcard.layout.received", False))
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = root / "test-results/fixture"
            case_id = "runner.phase.client-no-kubejs"
            runner.write_json(directory / "run.json", {"mode": "no-kubejs", "executionExit": 0, "phases": phases})
            runner.write_json(directory / "coverage-manifest.json", {"scenarios": []})
            for phase in ("client-smoke", "client-multiplayer"):
                runner.write_json(directory / phase / "client-results.json", {"success": True, "assertions": [
                    {"id": case_id, "success": True, "required": False},
                    {"id": "client.postcard.layout.received", "success": True, "required": False}]})
            with patch.object(report, "ROOT", root), patch("builtins.print"):
                self.assertFalse(report.generate("fixture"))
            result = runner.read_json(directory / "report.json")
            case = next(case for case in result["cases"] if case["id"] == case_id)
            self.assertEqual(("FAIL", True, True), (case["status"], case["required"], case["inScope"]))

    def test_complete_no_kubejs_run_passes_without_server_gametests(self):
        phases, _ = self.run_network_fixture()
        self.assertEqual({p for pair in self.PHASES for p in pair}, set(phases))
        for phase, result in phases.items():
            self.assertTrue(result["success"], phase)
            self.assertEqual(len(self.required(phase)), result["requiredCases"])
            self.assertEqual([], result["missingCases"])
            self.assertEqual([], result["failedCases"])

    def test_existing_frozen_manifest_is_used_without_overwriting_or_live_fallback(self):
        frozen = copy.deepcopy(self.coverage)
        frozen["scenarios"].append({"id": "fixture.frozen.requirement", "required": True,
                                    "phases": ["client-no-kubejs-rejoin"]})
        phases, _ = self.run_network_fixture(frozen_coverage=frozen)
        self.assertTrue(phases["client-no-kubejs"]["success"])
        self.assertFalse(phases["client-no-kubejs-rejoin"]["success"])
        self.assertEqual(["fixture.frozen.requirement"], phases["client-no-kubejs-rejoin"]["missingCases"])
        self.assertEqual(len(self.required("client-no-kubejs-rejoin")) + 1,
                         phases["client-no-kubejs-rejoin"]["requiredCases"])

    def test_invalid_manifest_fails_before_starting_any_process(self):
        for content in (None, {}, {"scenarios": []}):
            with self.subTest(content=content), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                if content is not None:
                    runner.write_json(root / "tests/scenarios/common/coverage-no-kubejs.json", content)
                with patch.object(runner, "ROOT", root), patch.object(runner, "stage") as stage:
                    with self.assertRaisesRegex(ValueError, "no-KubeJS coverage manifest"):
                        runner.run_multiplayer({}, {}, root / "game", root / "results", "host", 1, None, no_kubejs=True)
                    stage.assert_not_called()

    def test_scripted_multiplayer_does_not_apply_no_kubejs_gate(self):
        phases, _ = self.run_network_fixture(no_kubejs=False)
        self.assertEqual({"dedicated-server", "dedicated-server-restart", "client-multiplayer", "client-multiplayer-rejoin"},
                         set(phases))
        for result in phases.values():
            self.assertTrue(result["success"])
            self.assertNotIn("requiredCases", result)
            self.assertNotIn("coverageManifest", result)


class ReportGateTests(unittest.TestCase):
    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory()
        self.addCleanup(self.workspace.cleanup)
        self.original_root = report.ROOT
        self.addCleanup(setattr, report, "ROOT", self.original_root)
        report.ROOT = Path(self.workspace.name)
        self.directory = report.ROOT / "test-results/fixture"
        self.directory.mkdir(parents=True)
        self.write("tests/scenarios/common/coverage.json", {"branch": "1.20.1-forge", "scenarios": []})
        self.write("test-results/fixture/run.json", {"mode": "client", "scenario": "client-smoke",
                   "exitCode": 0, "executionExit": 0, "windowsFontBootLayer": False, "phases": {
                       phase: {"exitCode": 0, "success": True}
                       for phase in ("server", "client-smoke", "client-rejoin")}})
        for phase in ("server", "client-smoke", "client-rejoin"):
            directory = self.directory / phase
            directory.mkdir()
            (directory / ("server.log" if phase == "server" else "client.log")).write_text("正常启动\n", encoding="utf-8")
            if phase.startswith("client-"):
                self.write(f"test-results/fixture/{phase}/client-results.json", {"success": True, "message": "completed"})

    def write(self, name, value):
        path = report.ROOT / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")

    def cases(self):
        return {case["id"]: case for case in json.loads((self.directory / "report.json").read_text(encoding="utf-8"))["cases"]}

    def test_complete_phases_pass(self):
        self.assertTrue(report.generate("fixture"))

    def test_third_process_escrow_audit_requires_its_own_runtime_and_case(self):
        run = json.loads((self.directory / "run.json").read_text(encoding="utf-8"))
        run["escrowAudit"] = True
        self.write("test-results/fixture/run.json", run)
        self.write("test-results/fixture/coverage-manifest.json", {"scenarios": [{
            "id": "server.features.escrow_audit", "scope": "server", "required": True,
            "description": "Third process persisted claim without escrow replay", "phases": ["client-escrow-rejoin"]}]})
        self.assertFalse(report.generate("fixture"))
        self.assertEqual("FAIL", self.cases()["runner.phase.client-escrow-rejoin"]["status"])
        run["phases"]["client-escrow-rejoin"] = {"exitCode": 0, "success": True}
        self.write("test-results/fixture/run.json", run)
        self.write("test-results/fixture/client-escrow-rejoin/client-results.json", {"success": True})
        (self.directory / "client-escrow-rejoin/client.log").write_text("normal startup\n", encoding="utf-8")
        self.assertFalse(report.generate("fixture"))
        self.assertEqual("NOT_RUN", self.cases()["server.features.escrow_audit"]["status"])
        self.write("test-results/fixture/client-escrow-rejoin/server-results.json", {
            "cases": {"server.features.escrow_audit": {"success": True}}})
        self.assertTrue(report.generate("fixture"))

    def prepare_network_report_fixture(self, mode):
        original = runner.read_json(runner.ROOT / "tests/scenarios/common/coverage.json")
        manifest = runner.network_coverage_manifest(original) if mode == "multiplayer" else original
        phases = ["server", "dedicated-server", "dedicated-server-restart",
                  "client-multiplayer", "client-multiplayer-rejoin"]
        if mode == "suite":
            phases += ["client-smoke", "client-rejoin", "client-escrow-rejoin", "client-parcel-autofill",
                       "client-red-packet-autofill", "client-postcard-autofill",
                       "dedicated-no-kubejs", "dedicated-no-kubejs-restart",
                       "client-no-kubejs", "client-no-kubejs-rejoin"]
        assertion_phases = set(phases) | {"client-smoke", "client-rejoin"}
        assertions = [{"id": entry["id"], "success": True, "message": "Synthetic report-gate evidence"}
                      for entry in manifest["scenarios"] if entry.get("required")
                      and set(entry.get("phases", [])) & assertion_phases]
        primary = "client-smoke" if mode == "suite" else "client-multiplayer"
        for phase in phases:
            directory = self.directory / phase
            directory.mkdir(exist_ok=True)
            client = phase.startswith("client-")
            (directory / ("client.log" if client else "server.log")).write_text("normal startup\n", encoding="utf-8")
            self.write(f"test-results/fixture/{phase}/" + ("client-results.json" if client else "server-results.json"),
                       {"success": True, "assertions": assertions if phase == primary else []})
        self.write("test-results/fixture/run.json", {"mode": mode, "executionExit": 0, "exitCode": 0, "escrowAudit": mode == "suite",
                   "phases": {phase: {"success": True, "exitCode": 0} for phase in phases}})
        self.write("test-results/fixture/coverage-manifest.json", manifest)
        return original, manifest, primary

    def test_network_filter_keeps_old_required_cases_and_missing_evidence_fails(self):
        original, manifest, primary = self.prepare_network_report_fixture("multiplayer")
        self.assertEqual([entry["id"] for entry in original["scenarios"]],
                         [entry["id"] for entry in manifest["scenarios"]])
        for before, after in zip(original["scenarios"], manifest["scenarios"], strict=True):
            if before["id"].startswith(("client.navigation.", "client.mailbox.binding_ui.", "client.additional_ui.", "client.jade.mailbox.", "client.autofill.lifecycle.")):
                self.assertEqual({key: value for key, value in before.items() if key != "phases"},
                                 {key: value for key, value in after.items() if key != "phases"})
                self.assertEqual([phase for phase in before["phases"]
                                  if phase not in ("client-smoke", "client-rejoin")], after["phases"])
            else:
                self.assertEqual(before, after)
        self.assertTrue(report.generate("fixture"))
        path = self.directory / primary / "client-results.json"
        complete = runner.read_json(path)
        for missing in ("client.multiplayer.separate_process", "client.binder.button_and_tooltip"):
            with self.subTest(missing=missing):
                self.assertTrue(any(assertion["id"] == missing for assertion in complete["assertions"]))
                self.write(path.relative_to(report.ROOT).as_posix(), {**complete, "assertions": [
                    assertion for assertion in complete["assertions"] if assertion["id"] != missing]})
                self.assertFalse(report.generate("fixture"))
                case = self.cases()[missing]
                self.assertEqual(("NOT_RUN", True, True), (case["status"], case["required"], case["inScope"]))

    def test_suite_still_requires_integrated_ui_cases_after_network_derivation(self):
        original, manifest, primary = self.prepare_network_report_fixture("suite")
        self.assertEqual(original, manifest)
        self.assertTrue(report.generate("fixture"))
        path = self.directory / primary / "client-results.json"
        complete = runner.read_json(path)
        for missing in ("client.navigation.parcel.back.prepare", "client.mailbox.binding_ui.restored",
                        "client.additional_ui.config.empty_reopen", "client.additional_ui.unlock_tags.verify_saved",
                        "client.jade.mailbox.native_overlay", "client.autofill.lifecycle.parcel_multitick"):
            with self.subTest(missing=missing):
                self.assertTrue(any(assertion["id"] == missing for assertion in complete["assertions"]))
                self.write(path.relative_to(report.ROOT).as_posix(), {**complete, "assertions": [
                    assertion for assertion in complete["assertions"] if assertion["id"] != missing]})
                self.assertFalse(report.generate("fixture"))
                case = self.cases()[missing]
                self.assertEqual(("NOT_RUN", True, True), (case["status"], case["required"], case["inScope"]))

    def test_production_jar_requires_actual_source_and_exact_hash_in_every_phase(self):
        run = json.loads((self.directory / "run.json").read_text(encoding="utf-8"))
        run["productionJar"] = {"sha256": "expected-artifact-hash"}
        self.write("test-results/fixture/run.json", run)
        for phase in ("server", "client-smoke", "client-rejoin"):
            filename = "client-results.json" if phase.startswith("client-") else "server-results.json"
            self.write(f"test-results/fixture/{phase}/{filename}", {"success": True, "runtime": {
                "artifact": {"matchesExpected": True, "sha256": "expected-artifact-hash", "codeSource": "production.jar"},
            }})
        self.assertTrue(report.generate("fixture"))
        self.write("test-results/fixture/client-rejoin/client-results.json", {"success": True, "runtime": {
            "artifact": {"matchesExpected": False, "sha256": "different-build", "codeSource": "build/classes"},
        }})
        self.assertFalse(report.generate("fixture"))
        self.assertEqual("FAIL", self.cases()["runtime.production_jar.client-rejoin"]["status"])

    def test_frozen_manifest_preserves_historical_requirements(self):
        self.write("test-results/fixture/coverage-manifest.json", {"scenarios": []})
        self.write("tests/scenarios/common/coverage.json", {"scenarios": [{
            "id": "future.new_requirement", "required": True, "scope": "client",
            "phases": ["client-smoke"], "description": "A new test added after this run",
        }]})
        self.assertTrue(report.generate("fixture"))
        self.assertNotIn("future.new_requirement", self.cases())

    def test_missing_current_requirement_fails_for_legacy_run(self):
        self.write("tests/scenarios/common/coverage.json", {"scenarios": [{
            "id": "current.required", "required": True, "scope": "client",
            "phases": ["client-smoke"], "description": "Missing evidence must fail",
        }]})
        self.assertFalse(report.generate("fixture"))
        self.assertEqual("NOT_RUN", self.cases()["current.required"]["status"])

    def test_rejoin_encoding_error_fails_even_with_success_json(self):
        (self.directory / "client-rejoin/client.log").write_text(
            "Failed encoding custom payload contactquests:sync_team_extension: java.util.ConcurrentModificationException\n",
            encoding="utf-8")
        self.assertFalse(report.generate("fixture"))
        self.assertEqual("FAIL", self.cases()["runtime.log.client-rejoin"]["status"])

    def test_early_font_failure_fails_even_with_success_json(self):
        (self.directory / "client-rejoin/client.log").write_text(
            "[EARLYDISPLAY/]: Crash during font initialization\n"
            "java.nio.file.FileSystemNotFoundException: null\n", encoding="utf-8")
        self.assertFalse(report.generate("fixture"))
        self.assertEqual("FAIL", self.cases()["runtime.log.client-rejoin"]["status"])

    def test_forge_report_does_not_require_neoforge_font_modules(self):
        self.assertTrue(report.generate("fixture"))
        self.assertFalse(any(case.startswith("runtime.windows_font_module.") for case in self.cases()))
        manifest = report.read(report.ROOT / "tests/scenarios/common/coverage.json")
        self.assertEqual("1.20.1-forge", manifest["branch"])

    def test_windows_font_boot_layer_requires_actual_module_and_both_natives(self):
        run = json.loads((self.directory / "run.json").read_text(encoding="utf-8"))
        run["windowsFontBootLayer"] = True
        self.write("test-results/fixture/run.json", run)
        valid = {"name": "org.lwjgl.stb", "bootLayer": True,
                 "stbNativeBootLayer": True, "freetypeNativeBootLayer": True, "allocatorOpenToMinecraft": True}
        for phase in ("client-smoke", "client-rejoin"):
            self.write(f"test-results/fixture/{phase}/client-results.json",
                       {"success": True, "runtime": {"fontModule": valid}})
        self.assertTrue(report.generate("fixture"))
        for field in ("bootLayer", "stbNativeBootLayer", "freetypeNativeBootLayer", "allocatorOpenToMinecraft"):
            with self.subTest(field=field):
                self.write("test-results/fixture/client-rejoin/client-results.json",
                           {"success": True, "runtime": {"fontModule": dict(valid, **{field: False})}})
                self.assertFalse(report.generate("fixture"))
                self.assertEqual("FAIL", self.cases()["runtime.windows_font_module.client-rejoin"]["status"])

    def test_initialization_crash_fails_even_with_success_json(self):
        (self.directory / "client-smoke/client.log").write_text(
            "java.lang.ExceptionInInitializerError: null\n"
            "Caused by: java.lang.reflect.InaccessibleObjectException: module org.lwjgl does not open its allocator\n",
            encoding="utf-8")
        self.assertFalse(report.generate("fixture"))
        self.assertEqual("FAIL", self.cases()["runtime.log.client-smoke"]["status"])

    def test_client_crash_without_any_result_json_fails(self):
        (self.directory / "client-rejoin/client-results.json").unlink()
        self.assertFalse(report.generate("fixture"))
        self.assertEqual("FAIL", self.cases()["client.scenario.client-rejoin"]["status"])

    def test_skipped_rejoin_fails(self):
        run = json.loads((self.directory / "run.json").read_text(encoding="utf-8"))
        del run["phases"]["client-rejoin"]
        self.write("test-results/fixture/run.json", run)
        self.assertFalse(report.generate("fixture"))
        self.assertEqual("FAIL", self.cases()["runner.phase.client-rejoin"]["status"])

    def test_other_autofill_phase_network_error_fails(self):
        run = json.loads((self.directory / "run.json").read_text(encoding="utf-8"))
        run["scenario"] = "client-parcel-autofill"
        run["phases"]["client-parcel-autofill"] = {"exitCode": 0, "success": True}
        self.write("test-results/fixture/run.json", run)
        self.write("test-results/fixture/client-parcel-autofill/client-results.json", {"success": True})
        (self.directory / "client-parcel-autofill/client.log").write_text(
            "Internal Exception: io.netty.handler.codec.DecoderException: Failed to decode packet\n", encoding="utf-8")
        self.assertFalse(report.generate("fixture"))
        self.assertEqual("FAIL", self.cases()["runtime.log.client-parcel-autofill"]["status"])

    def test_dedicated_client_classloading_failure_fails(self):
        (self.directory / "server/server.log").write_text(
            "Attempted to load class net/minecraft/client/Minecraft for invalid dist DEDICATED_SERVER\n", encoding="utf-8")
        self.assertFalse(report.generate("fixture"))
        self.assertEqual("FAIL", self.cases()["runtime.log.server"]["status"])

    def test_missing_optional_classes_fail_even_with_success_json(self):
        for class_name in ["dev/latvian/mods/kubejs/event/KubeEvent", "dev/latvian/mods/rhino/Context",
                           "mezz/jei/api/IModPlugin"]:
            with self.subTest(class_name=class_name):
                (self.directory / "server/server.log").write_text(
                    "java.lang.NoClassDefFoundError: " + class_name + "\n", encoding="utf-8")
                self.assertFalse(report.generate("fixture"))
                self.assertEqual("FAIL", self.cases()["runtime.log.server"]["status"])

    def test_multiplayer_requires_server_restart_and_reconnect(self):
        run = json.loads((self.directory / "run.json").read_text(encoding="utf-8"))
        run["mode"] = "multiplayer"
        for phase in ("dedicated-server", "client-multiplayer", "dedicated-server-restart", "client-multiplayer-rejoin"):
            run["phases"][phase] = {"exitCode": 0, "success": True}
            directory = self.directory / phase
            directory.mkdir()
            (directory / ("client.log" if phase.startswith("client-") else "server.log")).write_text("normal startup\n", encoding="utf-8")
            if phase.startswith("client-"):
                self.write(f"test-results/fixture/{phase}/client-results.json", {
                    "success": True, "runtime": {"os": "Windows 11", "javaVersion": "21"},
                })
        self.write("tests/scenarios/common/coverage.json", {"scenarios": [
            {"id": "client.windows." + phase, "required": True, "scope": "client",
             "phases": [phase], "description": "Same Windows scenario on a dedicated server"}
            for phase in ("client-smoke", "client-rejoin")
        ]})
        self.write("test-results/fixture/run.json", run)
        self.assertTrue(report.generate("fixture"))
        self.assertEqual("PASS", self.cases()["client.windows.client-smoke"]["status"])
        self.assertEqual("PASS", self.cases()["client.windows.client-rejoin"]["status"])
        run["phases"]["dedicated-server-restart"]["success"] = False
        self.write("test-results/fixture/run.json", run)
        self.assertFalse(report.generate("fixture"))
        self.assertEqual("FAIL", self.cases()["runner.phase.dedicated-server-restart"]["status"])

    def test_without_kubejs_requires_absence_evidence_and_restart(self):
        phases = ("dedicated-no-kubejs", "dedicated-no-kubejs-restart", "client-no-kubejs", "client-no-kubejs-rejoin")
        run = {"mode": "no-kubejs", "exitCode": 0, "executionExit": 0,
               "phases": {phase: {"exitCode": 0, "success": True} for phase in phases}}
        self.write("test-results/fixture/run.json", run)
        self.write("test-results/fixture/coverage-manifest.json", {"scenarios": [
            {"id": "client.no_kubejs.dependencies_absent", "required": True, "scope": "client",
             "phases": ["client-no-kubejs"], "description": "Actual optional classes must be absent"},
        ]})
        for phase in phases:
            directory = self.directory / phase
            directory.mkdir()
            client = phase.startswith("client-")
            (directory / ("client.log" if client else "server.log")).write_text("normal startup\n", encoding="utf-8")
            self.write(f"test-results/fixture/{phase}/" + ("client-results.json" if client else "server-results.json"),
                       {"success": True, "loaded": True, "runtime": {"noKubejs": True},
                        "assertions": [{"id": "client.no_kubejs.dependencies_absent", "success": True}] if client else []})
        self.assertTrue(report.generate("fixture"))
        self.assertNotIn("kubejs.plugin.loaded", self.cases())
        run["phases"]["dedicated-no-kubejs-restart"]["success"] = False
        self.write("test-results/fixture/run.json", run)
        self.assertFalse(report.generate("fixture"))
        self.assertEqual("FAIL", self.cases()["runner.phase.dedicated-no-kubejs-restart"]["status"])
        run["phases"]["dedicated-no-kubejs-restart"]["success"] = True
        self.write("test-results/fixture/run.json", run)
        for phase in ("client-no-kubejs", "client-no-kubejs-rejoin"):
            self.write(f"test-results/fixture/{phase}/client-results.json", {"success": True, "runtime": {"noKubejs": True}})
        self.assertFalse(report.generate("fixture"))
        self.assertEqual("NOT_RUN", self.cases()["client.no_kubejs.dependencies_absent"]["status"])


if __name__ == "__main__":
    unittest.main()
