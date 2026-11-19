#!/usr/bin/env python3
"""Report explicit assertions; missing evidence is never a passing result."""
import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tests/runner"))
from network_checks import inspect_log


def read(path):
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {}


def generate(run_id):
    directory = ROOT / "test-results" / run_id
    run = read(directory / "run.json")
    coverage_path = directory / "coverage-manifest.json"
    frozen_coverage = coverage_path.is_file()
    if not frozen_coverage:
        coverage_path = ROOT / "tests/scenarios/common/coverage.json"
    coverage = read(coverage_path)
    cases = {}
    mode = run.get("mode")
    if mode == "suite":
        selected_phases = {"server", "client-smoke", "client-parcel-autofill",
                           "client-red-packet-autofill", "client-postcard-autofill", "client-rejoin",
                           "dedicated-server", "dedicated-server-restart", "client-multiplayer", "client-multiplayer-rejoin",
                           "dedicated-no-kubejs", "dedicated-no-kubejs-restart", "client-no-kubejs", "client-no-kubejs-rejoin"}
    elif mode == "no-kubejs":
        selected_phases = {"dedicated-no-kubejs", "dedicated-no-kubejs-restart", "client-no-kubejs", "client-no-kubejs-rejoin"}
    elif mode == "multiplayer":
        selected_phases = {"server", "dedicated-server", "dedicated-server-restart", "client-multiplayer", "client-multiplayer-rejoin"}
    elif mode == "client":
        selected_phases = {"server", run.get("scenario", "client-smoke")}
        if run.get("scenario", "client-smoke") == "client-smoke":
            selected_phases.add("client-rejoin")
    elif mode == "server":
        selected_phases = {"server"}
    else:
        selected_phases = set()
    if run.get("escrowAudit") and mode in ("suite", "client"):
        selected_phases.add("client-escrow-rejoin")

    def record(case_id, success, message, evidence, required=False):
        previous = cases.get(case_id)
        # Retain a failure when another phase repeats the same case successfully.
        if previous and previous["status"] == "FAIL" and success:
            return
        cases[case_id] = {"id": case_id, "status": "PASS" if success else "FAIL",
                          "message": message, "evidence": evidence.relative_to(ROOT).as_posix(),
                          "required": required or bool(previous and previous.get("required")), "inScope": True}

    phase_aliases = {"client-multiplayer": "client-smoke", "client-multiplayer-rejoin": "client-rejoin"}
    assertion_phases = selected_phases | {phase_aliases[phase] for phase in selected_phases if phase in phase_aliases}

    all_log_errors = []
    for phase in sorted(selected_phases):
        phase_data = run.get("phases", {}).get(phase, {})
        phase_ok = phase_data.get("exitCode") == 0 and phase_data.get("success", phase == "server")
        record("runner.phase." + phase, bool(phase_ok),
               "Completed required phase" if phase_ok else "Required phase failed or never completed",
               directory / "run.json", required=True)
        log = directory / phase / ("client.log" if phase.startswith("client-") else "server.log")
        errors = inspect_log(log)
        all_log_errors.extend(phase + ": " + error for error in errors)
        record("runtime.log." + phase, not errors,
               "\n".join(errors[:5]) if errors else "No startup, serialization, payload or classloading failure",
               log, required=True)
        if phase.startswith("client-"):
            path = directory / phase / "client-results.json"
            client = read(path)
            record("client.scenario." + phase, bool(client.get("success")),
                   client.get("message", "Client completion report is missing"), path, required=True)
            if run.get("windowsFontBootLayer"):
                font = client.get("runtime", {}).get("fontModule", {})
                valid = font.get("name") == "org.lwjgl.stb" and font.get("bootLayer") \
                    and font.get("stbNativeBootLayer") and font.get("freetypeNativeBootLayer") \
                    and font.get("allocatorOpenToMinecraft")
                record("runtime.windows_font_module." + phase, bool(valid),
                       "Windows font bindings and natives loaded from the boot layer: " + str(font),
                       path, required=True)
        if run.get("productionJar"):
            artifact_path = directory / phase / ("client-results.json" if phase.startswith("client-") else "server-results.json")
            artifact = read(artifact_path).get("runtime", {}).get("artifact", {})
            valid = artifact.get("matchesExpected") and artifact.get("sha256") == run["productionJar"]["sha256"]
            record("runtime.production_jar." + phase, bool(valid),
                   "Exact production jar loaded: " + str(artifact.get("codeSource", "missing runtime source")),
                   artifact_path, required=True)
    if selected_phases:
        record("server.log.persistence_and_network", not all_log_errors,
               "\n".join(all_log_errors[:10]) if all_log_errors else "All selected runtime logs passed",
               directory / "run.json", required=True)

    game_file = directory / "server/gametest-results.json"
    game = read(game_file)
    for name, result in game.get("cases", {}).items():
        method = name.rsplit(".", 1)[-1].lower()
        matching = next((entry["id"] for entry in coverage.get("scenarios", [])
                         if entry["id"].startswith("gametest.")
                         and entry["id"].split(".")[-1].lower() == method), None)
        record(matching or "gametest." + name.rsplit(".", 1)[-1], result.get("success", False),
               result.get("message", ""), game_file)
    for path in directory.glob("*/server-results.json"):
        server = read(path)
        if server.get("loaded") and not server.get("runtime", {}).get("noKubejs"):
            record("kubejs.plugin.loaded", True, "Binding and scripts loaded", path)
        for case_id, result in server.get("cases", {}).items():
            record(case_id, result.get("success", False), result.get("message", ""), path)
        for mail_type in server.get("interceptedTypes", []):
            if mail_type in ("parcel", "postcard", "red_packet"):
                record("kubejs.mail." + mail_type + ".intercept", True,
                       "Real mail event intercepted", path)
        server_cases = server.get("cases", {})
        for combined_id, member_ids in {
            "kubejs.mail.helpers": ["kubejs.mail.helpers." + kind
                                    for kind in ("parcel", "red_packet", "postcard")],
            "kubejs.npc.config.persistence": ["kubejs.npc.config.round_trip", "kubejs.npc.config.clear"],
        }.items():
            if all(member in server_cases for member in member_ids):
                record(combined_id, all(server_cases[member].get("success", False) for member in member_ids),
                       "; ".join(server_cases[member].get("message", "") for member in member_ids), path)
    for path in directory.glob("*/client-results.json"):
        client = read(path)
        name = path.parent.name
        runtime = client.get("runtime", {})
        platform = runtime.get("os", "unknown")
        record("client.scenario." + name, client.get("success", False),
               client.get("message", ""), path, required=name in selected_phases)
        if platform.startswith("Windows"):
            record("client.windows." + name, client.get("success", False),
                   platform + "; Java " + runtime.get("javaVersion", "unknown"), path)
            if name in phase_aliases:
                record("client.windows." + phase_aliases[name], client.get("success", False),
                       platform + "; executed the same scenario on a separate server", path)
        if client.get("ftbEditorGraphPassed"):
            record("client.ftb.quest_editor.full_graph", True,
                   "Task objects " + str(client.get("ftbEditorTaskCounts"))
                   + "; reward objects " + str(client.get("ftbEditorRewardCount")), path)
        type_name = next((kind for kind in ("parcel", "red_packet", "postcard")
                          if name == "client-" + kind.replace("_", "-") + "-autofill"), None)
        if type_name:
            record("client.contact." + type_name + "_autofill", client.get("success", False),
                   client.get("message", ""), path)
        for assertion in client.get("assertions", []):
            if assertion.get("id"):
                record(assertion["id"], assertion.get("success", False),
                       assertion.get("message", ""), path)
        if client.get("kubejsClientTicks"):
            record("kubejs.client.ticks", True, "Client KubeJS tick script executed", path)
    for entry in coverage.get("scenarios", []):
        case_id = entry["id"]
        if case_id not in cases:
            cases[case_id] = {"id": case_id, "status": "NOT_RUN",
                              "message": entry["description"], "evidence": ""}
        cases[case_id]["required"] = entry.get("required", False)
        cases[case_id]["inScope"] = bool(set(entry.get("phases", [])) & assertion_phases)
        cases[case_id]["scope"] = entry.get("scope", "")
    for case in cases.values():
        case.setdefault("required", False)
        case.setdefault("inScope", True)
    totals = {status: sum(c["status"] == status for c in cases.values())
              for status in ("PASS", "FAIL", "NOT_RUN")}
    assertion_gate = all(c["status"] == "PASS" for c in cases.values()
                         if c["required"] and c["inScope"])
    gate = run.get("executionExit", run.get("exitCode")) == 0 and assertion_gate
    required_totals = {status: sum(c["status"] == status and c["required"] and c["inScope"]
                                  for c in cases.values()) for status in totals}
    report = {"runId": run_id, "success": gate, "executionExit": run.get("exitCode"),
              "assertionsPassed": assertion_gate, "selectedPhases": sorted(selected_phases),
              "coverageManifest": coverage_path.relative_to(ROOT).as_posix(), "frozenCoverageManifest": frozen_coverage,
              "requiredTotals": required_totals, "totals": totals, "cases": list(cases.values()), "run": run}
    (directory / "report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n",
                                          encoding="utf-8")
    lines = ["# ContactQuests 自动化测试", "", f"运行：{run_id}", "",
             f"验收清单：{'使用该次运行保存的清单' if frozen_coverage else '历史运行没有保存清单，使用当前清单重新检查'}", "",
             f"验收：{'PASS' if gate else '未通过'}；执行退出码：{run.get('exitCode')}", "",
             f"PASS {totals['PASS']} / FAIL {totals['FAIL']} / NOT_RUN {totals['NOT_RUN']}", "",
             f"本次阶段必须项：PASS {required_totals['PASS']} / FAIL {required_totals['FAIL']} / NOT_RUN {required_totals['NOT_RUN']}", "",
             "| 项目 | 结果 | 本次必须通过 | 断言／证据 |", "|---|---|---|---|"]
    for case in cases.values():
        detail = (case["message"] + " " + case["evidence"]).replace("|", "\\|").replace("\n", " ")
        lines.append(f"| {case['id']} | {case['status']} | {'是' if case['required'] and case['inScope'] else '否'} | {detail} |")
    (directory / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Report: {directory / 'report.md'}; PASS={totals['PASS']}, FAIL={totals['FAIL']}, NOT_RUN={totals['NOT_RUN']}")
    return gate


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--run-id")
    group.add_argument("--latest", action="store_true")
    parser.add_argument("--check", action="store_true", help="Fail when an in-scope required assertion is missing or failed")
    args = parser.parse_args()
    run_id = read(ROOT / "test-results/latest.json").get("runId") if args.latest else args.run_id
    if not run_id:
        parser.error("No isolated test run has been recorded")
    success = generate(run_id)
    return 0 if success or not args.check else 1


if __name__ == "__main__":
    sys.exit(main())
