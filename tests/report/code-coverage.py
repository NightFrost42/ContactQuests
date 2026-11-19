#!/usr/bin/env python3
"""Report non-additive class identities and separately qualified source-line evidence."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PROJECT_PREFIX = 'com/creeperyang/contactquests/'
DUMP_NAME = re.compile(r'(.+)\.([0-9a-fA-F]{16})\.class\Z')


def crc_table():
    result = []
    for value in range(256):
        for _ in range(8):
            value = (value >> 1) ^ (0xd800000000000000 if value & 1 else 0)
        result.append(value)
    return result


CRC_TABLE = crc_table()


def class_id(data):
    """JaCoCo CRC64, including its Java 9 -> Java 8 version-byte workaround.

    This is the identity used in exec files, not a hash of a class's name.
    See org.jacoco.core.internal.data.CRC64.classId.
    """
    if len(data) > 7 and data[6:8] == b'\x00\x35':
        data = data[:7] + b'\x34' + data[8:]
    crc = 0
    for byte in data:
        crc = (crc >> 8) ^ CRC_TABLE[(crc ^ byte) & 0xff]
    return f'{crc:016x}'


def collect_classes(root, runtime_files):
    """Keep every (class name, class ID) rather than overwriting by class name."""
    project, hosts, baseline = {}, {}, {}

    def add(catalog, name, path, origin, expected_id=None):
        data = path.read_bytes()
        identity = class_id(data)
        if expected_id is not None and identity != expected_id.lower():
            raise ValueError(f'Dump class ID does not match its bytes: {path}')
        digest = hashlib.sha256(data).hexdigest()
        variants = catalog.setdefault(name, {})
        entry = variants.get(identity)
        if entry is not None and entry['sha256'] != digest:
            raise ValueError(f'Different class bytes share a JaCoCo ID: {name}:{identity}')
        if entry is None:
            entry = variants[identity] = {'path': path, 'sha256': digest, 'origins': []}
        if origin not in entry['origins']:
            entry['origins'].append(origin)
        return identity

    for language in ('java', 'kotlin'):
        compiled = root / 'build/classes' / language / 'main'
        for path in sorted(compiled.rglob('*.class')):
            name = path.relative_to(compiled).with_suffix('').as_posix()
            if not name.startswith(PROJECT_PREFIX):
                continue
            identity = add(project, name, path, f'compiled:{language}')
            if name in baseline and baseline[name] != identity:
                raise ValueError(f'Conflicting compiled production classes: {name}')
            baseline[name] = identity
    if not baseline:
        raise ValueError('Compiled production classes are required for the full denominator')

    for runtime in sorted(runtime_files):
        dumps = runtime.parent / 'coverage-classes'
        for path in sorted(dumps.rglob('*.class')):
            relative = path.relative_to(dumps)
            match = DUMP_NAME.fullmatch(relative.name)
            if match is None:
                raise ValueError(f'Dump lacks a JaCoCo class ID: {path}')
            name = relative.with_name(match[1]).as_posix()
            if name.startswith(PROJECT_PREFIX) and name not in baseline:
                raise ValueError(f'Runtime project class is absent from the compiled inventory: {name}')
            add(project if name in baseline else hosts, name, path,
                runtime.parent.name, match[2])
    return baseline, project, hosts


def identity_sets(catalog, baseline=None):
    """Pack variants into deterministic reports with at most one ID per class.

    These sets are analysis batches, not additional runs or totals to add. All
    exec files already merge probes for an identical ID across JVMs. The compiled
    set is kept intact; additional sets ensure every runtime ID gets analyzed.
    """
    sets = [dict(baseline)] if baseline is not None else []
    runtime = {name: sorted(identity for identity, entry in variants.items()
                            if any(not origin.startswith('compiled:') for origin in entry['origins']))
               for name, variants in catalog.items()}
    for index in range(max((len(ids) for ids in runtime.values()), default=0)):
        selected = dict(baseline or {})
        for name, ids in sorted(runtime.items()):
            if ids:
                selected[name] = ids[min(index, len(ids) - 1)]
        if selected not in sets:
            sets.append(selected)
    return sets or [{}]


def stage_classes(out, selected, catalog):
    # A content-addressed directory prevents stale classes from a prior report
    # or another class identity from leaking into this analysis.
    manifest = [(name, identity, catalog[name][identity]['sha256'])
                for name, identity in sorted(selected.items())]
    digest = hashlib.sha256(json.dumps(manifest).encode()).hexdigest()
    target_root = out / 'class-identities' / digest
    target_root.mkdir(parents=True, exist_ok=True)
    for name, identity, _ in manifest:
        entry = catalog[name][identity]
        data = entry['path'].read_bytes()
        if hashlib.sha256(data).hexdigest() != entry['sha256']:
            raise ValueError(f'Class bytes changed while preparing coverage: {entry["path"]}')
        target = target_root / (name + '.class')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    expected = {name + '.class' for name in selected}
    actual = {p.relative_to(target_root).as_posix() for p in target_root.rglob('*.class')}
    if actual != expected:
        raise ValueError(f'Stale or unexpected classes in identity set: {target_root}')
    return target_root


def counters(element):
    result = {node.attrib['type']: {key: int(node.attrib[key]) for key in ('missed', 'covered')}
              for node in element.findall('counter')}
    for value in result.values():
        total = value['missed'] + value['covered']
        value['percent'] = round(100 * value['covered'] / total, 2) if total else 0
    return result


def read_report(path, selected):
    report = ET.parse(path).getroot()
    classes = {}
    for package in report.findall('package'):
        for clazz in package.findall('class'):
            name = clazz.attrib['name']
            if name not in selected or name in classes:
                raise ValueError(f'Unexpected or duplicate class in JaCoCo report: {name}')
            classes[name] = {'classId': selected[name], 'counters': counters(clazz),
                             'sourceFile': clazz.get('sourcefilename')}
    # Keep source evidence separate for each identity set. Line numbers alone do
    # not establish equal instructions or branches across transformed bytecode.
    lines = [{'package': package.get('name', ''), 'sourceFile': source.get('name'),
              'lines': [dict(line.attrib) for line in source.findall('line')]}
             for package in report.findall('package') for source in package.findall('sourcefile')]
    return {'counters': counters(report), 'classes': classes, 'sources': lines}


def analyze(cli, merged, out, selected, catalog, xml, html, log, project):
    classes = stage_classes(out, selected, catalog)
    command = [*cli, 'report', str(merged), '--classfiles', str(classes)]
    if project:
        for language in ('java', 'kotlin'):
            command += ['--sourcefiles', str(ROOT / 'src/main' / language)]
    xml.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([*command, '--xml', str(xml), '--html', str(html)],
                   stdout=log, stderr=subprocess.STDOUT, check=True)
    result = read_report(xml, selected)
    result.update(xml=xml.relative_to(out).as_posix(), html=html.relative_to(out).as_posix(),
                  classIds=dict(sorted(selected.items())))
    return result


def source_metadata(path, root):
    try:
        if not path.resolve().is_relative_to(root.resolve()):
            return {'error': 'source_outside_project'}
        data = path.read_bytes()
        text = data.decode('utf-8-sig')
    except (OSError, UnicodeError) as error:
        return {'error': 'source_read_error', 'detail': str(error)}
    # Count physical CR/LF lines, not Unicode separators occurring in strings.
    parts = re.split(r'\r\n|\r|\n', text)
    return {'sha256': hashlib.sha256(data).hexdigest(),
            'physicalLineCount': len(parts) - (parts[-1] == '')}


def freeze_sources(root):
    """Freeze production source hashes before any identity reports are generated."""
    return {path.relative_to(root).as_posix(): source_metadata(path, root)
            for language in ('java', 'kotlin')
            for path in sorted((root / 'src/main' / language).rglob('*'))
            if path.is_file() and path.suffix in ('.java', '.kt')}


def load_source_manifest(path):
    """Accept the existing flat {repository-relative path: SHA-256} run freeze."""
    entries = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(entries, dict) or any(
            not isinstance(name, str) or not isinstance(digest, str)
            or re.fullmatch(r'[0-9a-fA-F]{64}', digest) is None
            for name, digest in entries.items()):
        raise ValueError('Source manifest must map repository-relative paths to SHA-256 hashes')
    return {name: digest.lower() for name, digest in entries.items()}


def source_line_evidence(root, reports, frozen_sources=None, expected_sources=None):
    """Union executable physical lines, never instructions or branches across IDs.

    A report's sourcefile aggregates every class using that package/sourceFile.
    ci > 0 establishes only execution somewhere on a physical source line, not
    execution of every instruction, lambda, statement or branch on that line.
    """
    frozen = freeze_sources(root) if frozen_sources is None else frozen_sources
    files, anomalies, resolved = {}, [], {}

    def resolve(package, filename):
        if (not isinstance(package, str) or (not package.startswith(PROJECT_PREFIX)
                and package != PROJECT_PREFIX.rstrip('/'))):
            return None, {'reason': 'source_outside_project_inventory'}
        if (any(part in ('', '.', '..') for part in package.split('/'))
                or '\\' in package or ':' in package
                or not isinstance(filename, str) or filename in ('', '.', '..')
                or any(char in filename for char in ('/', '\\', ':'))
                or Path(filename).suffix not in ('.java', '.kt')):
            return None, {'reason': 'invalid_source_path'}
        paths = [root / 'src/main' / language / package / filename
                 for language in ('java', 'kotlin')]
        candidates = [path for path in paths if path.is_file()]
        if len(candidates) != 1:
            return None, {'reason': 'ambiguous_source' if candidates else 'source_not_found',
                          'candidates': [path.relative_to(root).as_posix() for path in candidates]}
        path = candidates[0]
        relative = path.relative_to(root).as_posix()
        current = source_metadata(path, root)
        if 'error' in current:
            return None, {'reason': current['error'], 'path': relative}
        if relative not in frozen:
            return None, {'reason': 'source_not_frozen', 'path': relative}
        if current != frozen[relative]:
            return None, {'reason': 'source_changed_after_freeze', 'path': relative,
                          'frozen': frozen[relative], 'current': current}
        if expected_sources is not None and expected_sources.get(relative) != current['sha256']:
            return None, {'reason': 'source_manifest_mismatch', 'path': relative,
                          'expectedSha256': expected_sources.get(relative),
                          'actualSha256': current['sha256']}
        return dict(package=package, sourceFile=filename, path=relative, **current), None

    for report in reports:
        grouped = {}
        for source in report.get('sources', []):
            grouped.setdefault((source['package'], source['sourceFile']), []).append(source)
        for (package, filename), sources in sorted(grouped.items(), key=lambda pair: repr(pair[0])):
            context = {'report': report['xml'], 'package': package, 'sourceFile': filename}
            if len(sources) != 1:
                anomalies.append(dict(context, reason='duplicate_source_mapping'))
                continue
            # Even a dependency naming a project source cannot enter the union.
            owners = {name: identity for name, identity in report['classIds'].items()
                      if name.startswith(PROJECT_PREFIX) and name.rpartition('/')[0] == package
                      and report['classes'].get(name, {}).get('sourceFile') == filename
                      and report['classes'][name].get('classId') == identity}
            if not owners:
                anomalies.append(dict(context, reason='source_outside_project_inventory'))
                continue
            key = (package, filename)
            if key not in resolved:
                resolved[key] = resolve(package, filename)
            metadata, problem = resolved[key]
            if problem is not None:
                anomalies.append(dict(context, **problem))
                continue
            entry = files.setdefault(key, dict(metadata, executable=set(), covered=set(), observations=[]))
            executable, covered, seen = set(), set(), set()
            for line in sources[0]['lines']:
                try:
                    number, missed, executed = (int(line[key]) for key in ('nr', 'mi', 'ci'))
                    if number < 1 or min(missed, executed) < 0:
                        raise ValueError()
                except (KeyError, TypeError, ValueError):
                    anomalies.append(dict(context, reason='invalid_line_mapping', line=dict(line)))
                    continue
                if number > metadata['physicalLineCount']:
                    anomalies.append(dict(context, reason='line_out_of_range', line=number,
                                          physicalLineCount=metadata['physicalLineCount']))
                    continue
                if number in seen:
                    anomalies.append(dict(context, reason='duplicate_source_line', line=number))
                    # Conflicting duplicate evidence must not win by ordering.
                    executable.discard(number)
                    covered.discard(number)
                    continue
                seen.add(number)
                if missed + executed > 0:
                    executable.add(number)
                    if executed > 0:
                        covered.add(number)
            entry['executable'].update(executable)
            entry['covered'].update(covered)
            entry['observations'].append(dict(report=report['xml'], classIds=dict(sorted(owners.items())),
                                              executableLines=sorted(executable), coveredLines=sorted(covered)))

    result_files = []
    for _, entry in sorted(files.items()):
        executable, covered = entry.pop('executable'), entry.pop('covered')
        result_files.append(dict(entry, executableLines=sorted(executable), coveredLines=sorted(covered),
                                 unexecutedLines=sorted(executable - covered)))
    executable = sum(len(entry['executableLines']) for entry in result_files)
    covered = sum(len(entry['coveredLines']) for entry in result_files)
    return {'basis': 'physical-source-line-union-across-project-identity-reports',
            'sourceFreeze': {
                'basis': 'supplied-manifest-and-report-start-sha256' if expected_sources is not None
                         else 'report-start-sha256',
                'sha256': hashlib.sha256(json.dumps(frozen, sort_keys=True).encode()).hexdigest(),
                'fileCount': len(frozen)},
            'executable': executable, 'covered': covered, 'unexecuted': executable - covered,
            'percent': round(100 * covered / executable, 2) if executable else 0,
            'mappingComplete': not anomalies, 'files': result_files, 'anomalies': anomalies,
            'limitations': [
                'This is physical source-line execution evidence, not an exact cross-identity instruction or branch mapping.',
                'A line is executable if any project identity XML has mi+ci>0, and covered if any valid observation has ci>0.',
                'Several statements, lambdas or classes on one line count as one physical line; executing one does not prove all executed.',
                'Only unique, existing production sources with matching frozen bytes and in-range line numbers enter the denominator; anomalies are listed separately.',
                'Line numbers alone cannot prove a runtime transformation retained source semantics; keep per-identity XML and scenario assertions.',
                'Sources are frozen at report start; only a supplied pre-run SHA-256 manifest additionally checks agreement with that run freeze.',
                'Files or executable lines absent from every project identity XML cannot be inferred from source text and do not enter this denominator.',
                'Branch and instruction counters stay separate for each class identity; third-party host sources never enter this union.']}


def make_summary(run_id, runtime_files, baseline, catalog, reports, host_reports, *,
                 root=None, source_snapshot=None, source_manifest=None):
    identities = {}
    for name, variants in sorted(catalog.items()):
        identities[name] = []
        for identity, entry in sorted(variants.items()):
            observations = [report['classes'][name] for report in reports
                            if name in report['classes'] and report['classIds'][name] == identity]
            # One ID has one merged probe vector. Never choose the better of
            # disagreeing reports for the same ID.
            if observations and any(value != observations[0] for value in observations):
                raise ValueError(f'Inconsistent coverage for class identity {name}:{identity}')
            identities[name].append({'classId': identity, 'sha256': entry['sha256'],
                'compiled': baseline[name] == identity, 'origins': sorted(entry['origins']),
                'reports': [report['xml'] for report in reports if report['classIds'].get(name) == identity],
                'counters': observations[0]['counters'] if observations else {},
                'sourceFile': observations[0]['sourceFile'] if observations else None})
    unexecuted = [name for name, variants in identities.items()
                  if all('INSTRUCTION' in variant['counters'] for variant in variants)
                  and not any(variant['counters'].get('INSTRUCTION', {}).get('covered', 0)
                              for variant in variants)]
    unknown = [name for name, variants in identities.items()
               if any('INSTRUCTION' not in variant['counters'] for variant in variants)]
    return {'runId': run_id, 'counters': reports[0]['counters'],
            'unexecutedClasses': unexecuted, 'runtimeFiles': runtime_files,
            'counterBasis': 'compiled-production-class-identities',
            'unexecutedClassBasis': 'no covered instructions in any analyzed class identity',
            'unreportedClasses': unknown,
            'identityReports': reports, 'classIdentities': identities,
            'hostIdentityReports': host_reports,
            'sourceLineEvidence': source_line_evidence(root if root is not None else ROOT, reports,
                                                       source_snapshot, source_manifest),
            'limitations': [
                'counters and coverage.xml describe the intact compiled production identities only.',
                'Runtime identities are reported side by side; their counters are not additive and are not maximized or averaged.',
                'Identity sets are analysis batches using merged probes, not phase-specific execution totals.',
                'sourceLineEvidence is a separate physical-line union with validated source hashes and line ranges, not a replacement for compiled counters.',
                'Source instruction and branch data remain qualified by identity report; transformed instructions and branches have no proven cross-ID mapping.',
                'unexecutedClasses requires usable instruction counters and zero coverage for every identity; unreportedClasses lacks a usable counter for at least one identity.',
                'Dependency host identities are separate and never enter project counters or class status.',
                'The compiled inventory and source tree must correspond to the tested artifact; this script does not reconstruct historical builds.',
                'Mixin methods execute inside dependency host classes; see the separate host reports.',
                'Coverage records execution, while scenario assertions decide whether the behavior is correct.',
                'No claim of 100% code or edge-case coverage is made.']}


def markdown(summary):
    text = ['# 实际代码执行覆盖率', '',
            '主表与 coverage.xml 仅统计完整编译构件的类身份；运行时其他身份在下表并列，不能相加。', '',
            '| 类型 | 已执行 | 未执行 | 覆盖率 |', '|---|---:|---:|---:|']
    text += [f"| {kind} | {value['covered']} | {value['missed']} | {value['percent']}% |"
             for kind, value in summary['counters'].items()]
    evidence = summary['sourceLineEvidence']
    mapping_status = '完整' if evidence['mappingComplete'] else '不完整，比例仅描述有效映射子集'
    text += ['', '独立的源码物理行执行证据（不是跨身份的指令或分支覆盖率）：', '',
             f"- 有执行证据 {evidence['covered']} / 可执行物理行并集 {evidence['executable']}"
             f"（{evidence['percent']}%）；各身份均无执行证据 {evidence['unexecuted']} 行。",
             f"- 源码映射异常 {len(evidence['anomalies'])} 项，异常记录不进入上述并集；"
             '文件哈希、逐行集合及各身份观察见 summary.json/sourceLineEvidence。',
             f'- 源码映射状态：{mapping_status}。',
             '- 相同行上任一语句执行即有该行执行证据，不代表同一行的全部语句或分支已执行。',
             '- 源码在报告开始时按 SHA-256 冻结；使用 --source-manifest 可进一步核对指定运行前的冻结清单。']
    text += ['', '各类身份分析批次（均合并相同 class ID 在所有 JVM 的探针；不是各阶段独立成绩）：', '',
             '| 报告 | LINE 已执行/总数 | BRANCH 已执行/总数 |', '|---|---:|---:|']
    for report in summary['identityReports']:
        values = []
        for kind in ('LINE', 'BRANCH'):
            value = report['counters'].get(kind, {'covered': 0, 'missed': 0})
            values.append(f"{value['covered']}/{value['covered'] + value['missed']}")
        text.append(f"| [{report['xml']}]({report['html']}/index.html) | {' | '.join(values)} |")
    text += ['', '同名多身份生产类（class ID、来源和逐身份计数见 summary.json/classIdentities）：', '']
    text += ['- ' + name for name, variants in summary['classIdentities'].items() if len(variants) > 1]
    text += ['', '所有已分析身份均未执行的生产类：', '',
             *['- ' + name for name in summary['unexecutedClasses']], '',
             '无可用指令计数的类（不判定为未执行）：', '',
             *['- ' + name for name in summary['unreportedClasses']], '',
             '第三方宿主仅见 mixin-hosts-html 与 hostIdentityReports，不进入项目分母。',
             '未按行号推算跨身份分支或指令并集；完整限制见 summary.json/limitations。',
             '执行覆盖率不等于功能正确性，结果必须结合场景断言报告核对。']
    return '\n'.join(text) + '\n'


def _host_path(value):
    text = str(value)
    if os.name == 'nt' and text.startswith('/mnt/'):
        return re.sub(r'^/mnt/([a-zA-Z])/', lambda m: m[1].upper() + ':/', text)
    return text


def _resolve_java(launch):
    java = _host_path(launch['java'])
    if Path(java).is_file():
        return java
    if os.name == 'nt' and str(launch['java']).startswith('/'):
        candidates = sorted(
            [str(p) for p in [*Path.home().glob('.jdks/*/bin/java.exe'), *Path('C:/Users').glob('*/.jdks/*/bin/java.exe')] if p.is_file()]
        )
        java21 = [p for p in candidates if re.search(r'(?:^|[^0-9])21(?:[._-]|$)', Path(p).parents[1].name)]
        if java21:
            return java21[-1]
        if candidates:
            return candidates[-1]
        return shutil.which('java') or java
    return java


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--source-manifest', type=Path,
                        help='Optional pre-run flat path-to-SHA256 source freeze; mismatches are excluded and reported')
    args = parser.parse_args(argv)
    directory = ROOT / 'test-results' / args.run_id
    launch = json.loads((ROOT / 'build/contactquests-tests/runGameTestServer.json').read_text(encoding='utf-8'))
    if not launch.get('coverageCli'):
        raise SystemExit('Fresh launch records with the JaCoCo runtime are required')
    files = sorted(directory.glob('*/coverage.exec'))
    if not files:
        raise SystemExit('No runtime coverage was recorded')
    source_snapshot = freeze_sources(ROOT)
    source_manifest = load_source_manifest(args.source_manifest) if args.source_manifest else None
    out = directory / 'code-coverage'
    out.mkdir(exist_ok=True)
    cli = [_resolve_java(launch), '-jar', _host_path(launch['coverageCli'])]
    merged = out / 'merged.exec'
    subprocess.run([*cli, 'merge', *map(str, files), '--destfile', str(merged)], check=True)
    baseline, project, hosts = collect_classes(ROOT, files)
    reports, host_reports = [], []
    with (out / 'report.log').open('w', encoding='utf-8') as log:
        for index, selected in enumerate(identity_sets(project, baseline)):
            base = out if index == 0 else out / 'identity-reports' / f'runtime-{index}'
            reports.append(analyze(cli, merged, out, selected, project, base / 'coverage.xml', base / 'html', log, True))
        for index, selected in enumerate(identity_sets(hosts)):
            base = out if index == 0 else out / 'identity-reports' / f'hosts-{index}'
            host_reports.append(analyze(cli, merged, out, selected, hosts, base / 'mixin-hosts.xml',
                                        base / 'mixin-hosts-html', log, False))
    summary = make_summary(args.run_id, [f.relative_to(ROOT).as_posix() for f in files], baseline, project, reports, host_reports,
                           source_snapshot=source_snapshot, source_manifest=source_manifest)
    (out / 'summary.json').write_text(json.dumps(summary, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    (out / 'coverage.md').write_text(markdown(summary), encoding='utf-8')
    print('Compiled-identity coverage (other identities reported separately):', summary['counters'])
    evidence = summary['sourceLineEvidence']
    print('Separate physical source-line evidence:',
          {key: evidence[key] for key in ('covered', 'executable', 'unexecuted', 'percent')},
          'mapping anomalies:', len(evidence['anomalies']))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
