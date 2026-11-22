"""Fixed synthetic coverage/report fixtures; no JVM, provider, game or build is run."""
import importlib.util
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch
import xml.etree.ElementTree as ET


SPEC = importlib.util.spec_from_file_location(
    'cq_code_coverage', Path(__file__).with_name('code-coverage.py'))
coverage = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(coverage)

PREFIX = 'com/creeperyang/contactquests/'
WIDGET = PREFIX + 'client/Widget'
NEVER = PREFIX + 'NeverRun'
HOST = 'dev/ftb/mods/ftbquests/quest/Quest'


def write_xml(path, selections, counts, missing=()):
    """Each class has a fixed denominator; counts differ by selected bytes."""
    root = ET.Element('report', name='synthetic')
    packages = {}
    totals = {kind: [0, 0] for kind in ('INSTRUCTION', 'BRANCH', 'LINE')}
    for name, identity in sorted(selections.items()):
        package_name, _, simple = name.rpartition('/')
        package = packages.get(package_name)
        if package is None:
            package = packages[package_name] = ET.SubElement(root, 'package', name=package_name)
        clazz = ET.SubElement(package, 'class', name=name, sourcefilename=simple + '.kt')
        if name in missing:
            continue
        values = counts.get((name, identity), {'INSTRUCTION': (10, 0), 'BRANCH': (2, 0), 'LINE': (2, 0)})
        for kind, (missed, covered) in values.items():
            ET.SubElement(clazz, 'counter', type=kind, missed=str(missed), covered=str(covered))
            totals[kind][0] += missed
            totals[kind][1] += covered
        source = ET.SubElement(package, 'sourcefile', name=simple + '.kt')
        ET.SubElement(source, 'line', nr='12', mi=str(values['INSTRUCTION'][0]),
                      ci=str(values['INSTRUCTION'][1]), mb=str(values['BRANCH'][0]),
                      cb=str(values['BRANCH'][1]))
    for kind, (missed, covered) in totals.items():
        ET.SubElement(root, 'counter', type=kind, missed=str(missed), covered=str(covered))
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(root).write(path, encoding='utf-8')


class FixtureCase(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        root_patch = patch.object(coverage, 'ROOT', self.root)
        root_patch.start()
        self.addCleanup(root_patch.stop)

    def put(self, relative, content):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return path

    def compiled(self, name=WIDGET, data=b'compiled-widget', language='kotlin'):
        return self.put(f'build/classes/{language}/main/{name}.class', data)

    def runtime(self, phase, name=WIDGET, data=b'client-widget'):
        execution = self.put(f'test-results/fixture/{phase}/coverage.exec', b'fixed-exec')
        identity = coverage.class_id(data)
        self.put(f'test-results/fixture/{phase}/coverage-classes/{name}.{identity}.class', data)
        return execution


class JavaRuntimeTests(FixtureCase):
    def test_report_prefers_java17_from_java_home_over_exported_java21(self):
        for platform, executable in (("posix", "java"), ("nt", "java.exe")):
            with self.subTest(platform=platform):
                java17 = self.put(f"vendor-{platform}/bin/{executable}", b"")
                self.put(f"vendor-{platform}/release", b'JAVA_VERSION="17.0.13"\n')
                java21 = self.put(f"jdk-21-{platform}/bin/{executable}", b"")
                self.put(f"jdk-21-{platform}/release", b'JAVA_VERSION="21.0.5"\n')
                with patch.object(coverage, 'os', SimpleNamespace(name=platform, environ={'JAVA_HOME': str(java17.parents[1])})), \
                        patch.object(coverage.Path, 'glob', return_value=[]):
                    self.assertEqual(str(java17), coverage._resolve_java({'java': str(java21)}))
                    self.assertEqual(str(java17), coverage._resolve_java({'java': str(java17)}))

    def test_report_finds_windows_jdk17_without_selecting_misnamed_java21(self):
        java = self.put('jdk-17.0.13/bin/java.exe', b'')
        newer = self.put('z-jdk-17-misnamed/bin/java.exe', b'')
        self.put('z-jdk-17-misnamed/release', b'JAVA_VERSION="21.0.5"\n')
        with patch.object(coverage, 'os', SimpleNamespace(name='nt', environ={})), \
                patch.object(coverage.Path, 'glob', return_value=[newer, java]):
            self.assertEqual(str(java), coverage._resolve_java({'java': '/usr/lib/jvm/jdk-17/bin/java'}))


class ForgeReportTests(FixtureCase):
    def setUp(self):
        super().setUp()
        spec = importlib.util.spec_from_file_location('cq_forge_report', Path(__file__).with_name('generate-report.py'))
        self.report = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.report)
        self.report.ROOT = self.root
        self.directory = self.root / 'test-results/forge-devjar'
        self.jar_name = 'contactquests-2.1.6-1.20.1-dev.jar'
        self.digest = hashlib.sha256(b'forge-devjar-fixture').hexdigest()
        self.phases = ('server', 'client-smoke', 'client-rejoin')
        self.write('run.json', {
            'mode': 'client', 'scenario': 'client-smoke', 'clientPlatform': 'windows',
            'windowsFontBootLayer': False, 'executionExit': 0, 'exitCode': 0,
            'productionJar': {'path': 'build/libs/' + self.jar_name, 'sha256': self.digest},
            'phases': {phase: {'exitCode': 0, 'success': True} for phase in self.phases},
        })
        self.write('coverage-manifest.json', {'branch': '1.20.1-forge', 'scenarios': []})
        for phase in self.phases:
            side = 'client' if phase.startswith('client-') else 'server'
            self.put(f'test-results/forge-devjar/{phase}/{side}.log', b'Forge started normally\n')
            self.write(f'{phase}/{side}-results.json', {'success': True, 'runtime': {'artifact': {
                'matchesExpected': True, 'sha256': self.digest, 'codeSource': 'mods/' + self.jar_name,
            }}})

    def write(self, relative, data):
        self.put('test-results/forge-devjar/' + relative, json.dumps(data).encode('utf-8'))

    def test_forge_devjar_passes_without_windows_font_boot_layer(self):
        with patch('builtins.print'):
            self.assertTrue(self.report.generate('forge-devjar'))
        result = self.report.read(self.directory / 'report.json')
        cases = {case['id']: case for case in result['cases']}
        self.assertFalse(any(name.startswith('runtime.windows_font_module.') for name in cases))
        for phase in self.phases:
            self.assertEqual('PASS', cases['runtime.production_jar.' + phase]['status'])
        self.assertFalse(result['run']['windowsFontBootLayer'])

    def test_forge_devjar_requires_both_runtime_match_and_exact_hash_in_each_phase(self):
        for phase in self.phases:
            side = 'client' if phase.startswith('client-') else 'server'
            relative = f'{phase}/{side}-results.json'
            original = self.report.read(self.directory / relative)
            for changes in ({'matchesExpected': False}, {'sha256': 'different-jar-hash'}):
                with self.subTest(phase=phase, changes=changes):
                    bad = json.loads(json.dumps(original))
                    bad['runtime']['artifact'].update(changes)
                    self.write(relative, bad)
                    with patch('builtins.print'):
                        self.assertFalse(self.report.generate('forge-devjar'))
                    cases = {case['id']: case for case in self.report.read(self.directory / 'report.json')['cases']}
                    self.assertEqual('FAIL', cases['runtime.production_jar.' + phase]['status'])
                    self.write(relative, original)


class ClassIdentityTests(FixtureCase):
    def test_jacoco_crc64_golden_vectors_and_java9_compatibility(self):
        self.assertEqual('0000000000000000', coverage.class_id(b''))
        self.assertEqual('46a5a9388a5beffe', coverage.class_id(b'123456789'))
        java8 = bytes.fromhex('cafebabe00000034') + b'fixed class payload'
        java9 = bytes.fromhex('cafebabe00000035') + b'fixed class payload'
        java10 = bytes.fromhex('cafebabe00000036') + b'fixed class payload'
        self.assertEqual(coverage.class_id(java8), coverage.class_id(java9))
        self.assertNotEqual(coverage.class_id(java8), coverage.class_id(java10))

    def test_different_client_server_identities_survive_without_overwrite(self):
        self.compiled()
        files = [self.runtime('client', data=b'client-widget'), self.runtime('server', data=b'server-widget'),
                 self.runtime('client-rejoin', data=b'client-widget')]
        baseline, project, hosts = coverage.collect_classes(self.root, files)
        self.assertEqual(3, len(project[WIDGET]))
        self.assertEqual(['client', 'client-rejoin'], project[WIDGET][coverage.class_id(b'client-widget')]['origins'])
        self.assertEqual({}, hosts)
        sets = coverage.identity_sets(project, baseline)
        self.assertEqual(baseline, sets[0])
        self.assertEqual(set(project[WIDGET]), {selection[WIDGET] for selection in sets})
        staged = [coverage.stage_classes(self.root / 'out', selection, project) for selection in sets]
        self.assertEqual(3, len(set(staged)))
        self.assertEqual({b'compiled-widget', b'client-widget', b'server-widget'},
                         {(path / (WIDGET + '.class')).read_bytes() for path in staged})

    def test_identical_ids_deduplicate_and_compiled_only_classes_stay(self):
        self.compiled(data=b'same-widget')
        self.compiled(NEVER, b'never')
        files = [self.runtime('client', data=b'same-widget'), self.runtime('server', data=b'same-widget')]
        baseline, project, _ = coverage.collect_classes(self.root, files)
        self.assertEqual(1, len(project[WIDGET]))
        self.assertEqual([baseline], coverage.identity_sets(project, baseline))
        self.assertIn(NEVER, baseline)

    def test_variant_batches_cover_every_id_deterministically(self):
        self.compiled()
        self.compiled(NEVER, b'never')
        files = [self.runtime('server', data=b'server'), self.runtime('client', data=b'client'),
                 self.runtime('other', name=NEVER, data=b'never-transformed')]
        baseline, project, _ = coverage.collect_classes(self.root, files)
        _, reversed_project, _ = coverage.collect_classes(self.root, list(reversed(files)))
        sets = coverage.identity_sets(project, baseline)
        self.assertEqual(sets, coverage.identity_sets(reversed_project, baseline))
        for name, variants in project.items():
            self.assertEqual(set(variants), {selection[name] for selection in sets})
        self.assertTrue(all(set(selection) == set(baseline) for selection in sets))

    def test_third_party_dump_never_enters_project_inventory(self):
        self.compiled()
        self.compiled(HOST, b'host-in-compiled-directory')
        baseline, project, hosts = coverage.collect_classes(self.root, [self.runtime('server', HOST, b'host')])
        self.assertNotIn(HOST, baseline)
        self.assertNotIn(HOST, project)
        self.assertIn(HOST, hosts)
        self.assertEqual({HOST}, set(coverage.identity_sets(hosts)[0]))

    def test_bad_dump_identity_is_rejected_instead_of_credited(self):
        self.compiled()
        execution = self.put('test-results/fixture/server/coverage.exec', b'exec')
        self.put(f'test-results/fixture/server/coverage-classes/{WIDGET}.0000000000000000.class', b'not-zero')
        with self.assertRaisesRegex(ValueError, 'does not match its bytes'):
            coverage.collect_classes(self.root, [execution])

    def test_dump_without_identity_is_rejected(self):
        self.compiled()
        execution = self.put('test-results/fixture/server/coverage.exec', b'exec')
        self.put(f'test-results/fixture/server/coverage-classes/{WIDGET}.class', b'no-id')
        with self.assertRaisesRegex(ValueError, 'lacks a JaCoCo class ID'):
            coverage.collect_classes(self.root, [execution])

    def test_runtime_project_class_missing_from_build_is_rejected(self):
        self.compiled()
        with self.assertRaisesRegex(ValueError, 'absent from the compiled inventory'):
            coverage.collect_classes(self.root, [self.runtime('client', NEVER, b'stale-class')])

    def test_missing_or_conflicting_compiled_inventory_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Compiled production classes are required'):
            coverage.collect_classes(self.root, [])
        self.compiled(data=b'kotlin')
        self.compiled(data=b'java', language='java')
        with self.assertRaisesRegex(ValueError, 'Conflicting compiled'):
            coverage.collect_classes(self.root, [])

    def test_identity_collision_with_different_bytes_is_rejected(self):
        self.compiled(data=b'first')
        self.compiled(data=b'second', language='java')
        with patch.object(coverage, 'class_id', return_value='1111111111111111'):
            with self.assertRaisesRegex(ValueError, 'Different class bytes share'):
                coverage.collect_classes(self.root, [])

    def test_stale_class_in_reused_identity_directory_is_rejected(self):
        self.compiled()
        baseline, project, _ = coverage.collect_classes(self.root, [])
        staged = coverage.stage_classes(self.root / 'out', baseline, project)
        (staged / 'Stale.class').write_bytes(b'old-report')
        with self.assertRaisesRegex(ValueError, 'Stale or unexpected'):
            coverage.stage_classes(self.root / 'out', baseline, project)

    def test_build_changed_after_inventory_is_not_mislabeled_as_old_identity(self):
        compiled = self.compiled()
        baseline, project, _ = coverage.collect_classes(self.root, [])
        compiled.write_bytes(b'a concurrent build replaced these bytes')
        with self.assertRaisesRegex(ValueError, 'Class bytes changed'):
            coverage.stage_classes(self.root / 'out', baseline, project)


class ReportSemanticsTests(FixtureCase):
    def reports(self, missing=()):
        self.compiled()
        self.compiled(NEVER, b'never')
        execution = self.runtime('client', data=b'client-widget')
        baseline, catalog, _ = coverage.collect_classes(self.root, [execution])
        counts = {(WIDGET, coverage.class_id(b'compiled-widget')):
                  {'INSTRUCTION': (10, 0), 'BRANCH': (2, 0), 'LINE': (2, 0)},
                  (WIDGET, coverage.class_id(b'client-widget')):
                  {'INSTRUCTION': (1, 9), 'BRANCH': (1, 1), 'LINE': (0, 2)}}
        reports = []
        for index, selection in enumerate(coverage.identity_sets(catalog, baseline)):
            path = self.root / f'identity-{index}.xml'
            write_xml(path, selection, counts, missing)
            report = coverage.read_report(path, selection)
            report.update(xml=path.name, html=f'html-{index}', classIds=selection)
            reports.append(report)
        return baseline, catalog, reports

    def test_runtime_execution_not_falsely_listed_as_unexecuted_or_added_to_baseline(self):
        baseline, catalog, reports = self.reports()
        summary = coverage.make_summary('fixture', ['client/coverage.exec'], baseline, catalog, reports, [])
        self.assertEqual({'missed': 20, 'covered': 0, 'percent': 0}, summary['counters']['INSTRUCTION'])
        self.assertEqual([NEVER], summary['unexecutedClasses'])
        self.assertEqual(9, summary['classIdentities'][WIDGET][
            next(i for i, v in enumerate(summary['classIdentities'][WIDGET]) if not v['compiled'])]['counters']['INSTRUCTION']['covered'])
        self.assertEqual(2, len(summary['identityReports']))
        self.assertEqual('compiled-production-class-identities', summary['counterBasis'])
        self.assertTrue({'runId', 'counters', 'unexecutedClasses', 'runtimeFiles', 'limitations'} <= summary.keys())

    def test_different_branch_shapes_remain_separate_instead_of_sum_or_max(self):
        baseline, catalog, reports = self.reports()
        reports[0]['sources'][0]['lines'][0].update(nr='12', mb='4', cb='0')
        reports[1]['sources'][0]['lines'][0].update(nr='12', mb='1', cb='1')
        reports[0]['counters']['BRANCH'] = {'missed': 4, 'covered': 0, 'percent': 0}
        reports[1]['counters']['BRANCH'] = {'missed': 1, 'covered': 1, 'percent': 50}
        summary = coverage.make_summary('fixture', [], baseline, catalog, reports, [])
        self.assertEqual(0, summary['counters']['BRANCH']['covered'])
        self.assertEqual(['4', '1'], [r['sources'][0]['lines'][0]['mb'] for r in summary['identityReports']])

    def test_unreported_class_is_unknown_not_an_unexecuted_claim(self):
        baseline, catalog, reports = self.reports(missing=(NEVER,))
        summary = coverage.make_summary('fixture', [], baseline, catalog, reports, [])
        self.assertNotIn(NEVER, summary['unexecutedClasses'])
        self.assertEqual([NEVER], summary['unreportedClasses'])

    def test_missing_one_variant_counter_is_unknown_even_if_other_variant_is_zero(self):
        baseline, catalog, reports = self.reports()
        reports[1]['classes'][WIDGET]['counters'] = {}
        summary = coverage.make_summary('fixture', [], baseline, catalog, reports, [])
        self.assertNotIn(WIDGET, summary['unexecutedClasses'])
        self.assertEqual([WIDGET], summary['unreportedClasses'])

    def test_conflicting_results_for_same_id_cannot_select_better_count(self):
        baseline, catalog, reports = self.reports()
        duplicate = json.loads(json.dumps(reports[0]))
        duplicate['classes'][WIDGET]['counters']['INSTRUCTION']['covered'] = 5
        with self.assertRaisesRegex(ValueError, 'Inconsistent coverage'):
            coverage.make_summary('fixture', [], baseline, catalog, reports + [duplicate], [])

    def test_unexpected_host_in_project_xml_is_rejected(self):
        path = self.root / 'bad.xml'
        write_xml(path, {HOST: '1111111111111111'}, {})
        with self.assertRaisesRegex(ValueError, 'Unexpected or duplicate class'):
            coverage.read_report(path, {WIDGET: '2222222222222222'})

    def test_duplicate_class_in_xml_is_rejected_instead_of_replacing_counters(self):
        path = self.root / 'duplicate.xml'
        write_xml(path, {WIDGET: '2222222222222222'}, {})
        tree = ET.parse(path)
        package = tree.getroot().find('package')
        package.append(ET.fromstring(ET.tostring(package.find('class'))))
        tree.write(path)
        with self.assertRaisesRegex(ValueError, 'Unexpected or duplicate class'):
            coverage.read_report(path, {WIDGET: '2222222222222222'})

    def test_markdown_explicitly_labels_nonadditive_identity_reports(self):
        baseline, catalog, reports = self.reports()
        summary = coverage.make_summary('fixture', [], baseline, catalog, reports, [])
        text = coverage.markdown(summary)
        self.assertIn('不能相加', text)
        self.assertIn('不是各阶段独立成绩', text)
        self.assertIn('第三方宿主', text)
        self.assertIn('(html-1/index.html)', text)

    def test_main_uses_isolated_class_sets_and_keeps_legacy_summary_fields(self):
        self.compiled()
        self.compiled(NEVER, b'never')
        self.put(f'src/main/kotlin/{WIDGET}.kt', b'// widget\n' * 12)
        self.put(f'src/main/kotlin/{NEVER}.kt', b'// never\n' * 12)
        self.runtime('server', data=b'server-widget')
        self.runtime('client', data=b'client-widget')
        self.runtime('server', HOST, b'host-widget')
        launch = self.root / 'build/contactquests-tests/runGameTestServer.json'
        launch.parent.mkdir(parents=True)
        launch.write_text(json.dumps({'java': 'not-a-real-jvm', 'coverageCli': 'fake-cli'}), encoding='utf-8')
        manifest = self.put('source-freeze.json', json.dumps({
            path.relative_to(self.root).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in (self.root / 'src/main').rglob('*.kt')}).encode())
        report_inputs = []

        def fake_cli(command, **kwargs):
            if 'report' not in command:
                self.assertIn('merge', command)
                return
            classes = Path(command[command.index('--classfiles') + 1])
            selection = {p.relative_to(classes).with_suffix('').as_posix(): coverage.class_id(p.read_bytes())
                         for p in classes.rglob('*.class')}
            report_inputs.append(selection)
            counts = {(WIDGET, coverage.class_id(b'client-widget')):
                      {'INSTRUCTION': (0, 10), 'BRANCH': (0, 2), 'LINE': (0, 2)},
                      (HOST, coverage.class_id(b'host-widget')):
                      {'INSTRUCTION': (0, 10000), 'BRANCH': (0, 1000), 'LINE': (0, 1000)}}
            xml = Path(command[command.index('--xml') + 1])
            write_xml(xml, selection, counts)

        with patch.object(coverage, 'ROOT', self.root), patch.object(coverage.subprocess, 'run', side_effect=fake_cli):
            self.assertEqual(0, coverage.main(['--run-id', 'fixture', '--source-manifest', str(manifest)]))
        out = self.root / 'test-results/fixture/code-coverage'
        summary = json.loads((out / 'summary.json').read_text(encoding='utf-8'))
        self.assertEqual(0, summary['counters']['INSTRUCTION']['covered'])
        self.assertEqual([NEVER], summary['unexecutedClasses'])
        self.assertEqual(3, len(summary['identityReports']))
        self.assertEqual(10000, summary['hostIdentityReports'][0]['counters']['INSTRUCTION']['covered'])
        self.assertNotIn(HOST, summary['classIdentities'])
        self.assertEqual(2, summary['sourceLineEvidence']['executable'])
        self.assertEqual(1, summary['sourceLineEvidence']['covered'])
        self.assertEqual(1, summary['sourceLineEvidence']['unexecuted'])
        self.assertTrue(summary['sourceLineEvidence']['mappingComplete'])
        self.assertEqual('supplied-manifest-and-report-start-sha256',
                         summary['sourceLineEvidence']['sourceFreeze']['basis'])
        self.assertTrue(all(HOST not in selection for selection in report_inputs[:-1]))
        self.assertTrue((out / 'coverage.xml').is_file())
        self.assertTrue((out / 'mixin-hosts.xml').is_file())
        self.assertTrue((out / 'coverage.md').is_file())


class SourceLineEvidenceTests(FixtureCase):
    def source(self, filename='Widget.kt', lines=8, data=None, language='kotlin', package=None):
        package = package or WIDGET.rpartition('/')[0]
        return self.put(f'src/main/{language}/{package}/{filename}',
                        data if data is not None else b'// physical source line\n' * lines)

    def report(self, label, lines, identity='1111111111111111', filename='Widget.kt',
               package=None, owners=None):
        package = package or WIDGET.rpartition('/')[0]
        owners = owners or {WIDGET: identity}
        return {'xml': label + '.xml', 'classIds': dict(owners),
                'classes': {name: {'classId': value, 'sourceFile': filename} for name, value in owners.items()},
                'sources': [{'package': package, 'sourceFile': filename, 'lines': lines}]}

    def line(self, number, missed=1, covered=0, mb=0, cb=0):
        return {key: str(value) for key, value in
                dict(nr=number, mi=missed, ci=covered, mb=mb, cb=cb).items()}

    def evidence(self, reports, **kwargs):
        return coverage.source_line_evidence(self.root, reports, **kwargs)

    def reasons(self, evidence):
        return [item['reason'] for item in evidence['anomalies']]

    def test_union_uses_any_identity_execution_and_retains_unexecuted_lines(self):
        path = self.source()
        reports = [self.report('compiled', [self.line(1), self.line(2)]),
                   self.report('client', [self.line(1, 0, 80), self.line(3)], '2222222222222222'),
                   self.report('server', [self.line(4, 0, 3)], '3333333333333333')]
        result = self.evidence(reports)
        self.assertEqual((4, 2, 2, 50), tuple(result[key] for key in
                         ('executable', 'covered', 'unexecuted', 'percent')))
        entry = result['files'][0]
        self.assertEqual([1, 2, 3, 4], entry['executableLines'])
        self.assertEqual([1, 4], entry['coveredLines'])
        self.assertEqual([2, 3], entry['unexecutedLines'])
        self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), entry['sha256'])
        self.assertEqual(8, entry['physicalLineCount'])
        self.assertEqual([report['xml'] for report in reports],
                         [observation['report'] for observation in entry['observations']])
        self.assertTrue(result['mappingComplete'])

    def test_same_identity_repeated_in_batches_never_inflates_physical_lines(self):
        self.source()
        first = self.report('first', [self.line(1), self.line(2, 0, 10)])
        second = json.loads(json.dumps(first))
        second['xml'] = 'second.xml'
        result = self.evidence([first, second])
        self.assertEqual((2, 1), (result['executable'], result['covered']))
        self.assertEqual(2, len(result['files'][0]['observations']))

    def test_two_classes_and_lambdas_on_one_source_line_count_once(self):
        self.source()
        report = self.report('client', [self.line(2, 10, 1)],
                             owners={WIDGET: '1111111111111111', WIDGET + '$Lambda': '2222222222222222'})
        result = self.evidence([report])
        self.assertEqual((1, 1, 0), (result['executable'], result['covered'], result['unexecuted']))
        self.assertEqual(2, len(result['files'][0]['observations'][0]['classIds']))
        self.assertIn('does not prove all executed', ' '.join(result['limitations']))

    def test_same_filename_in_different_packages_is_not_merged(self):
        self.source()
        other_package = PREFIX.rstrip('/') + '/server'
        self.source(package=other_package)
        other = other_package + '/Other'
        result = self.evidence([self.report('client', [self.line(1, 0, 1)]),
                               self.report('server', [self.line(1)], package=other_package,
                                           owners={other: '3333333333333333'})])
        self.assertEqual((2, 1), (result['executable'], result['covered']))
        self.assertEqual(2, len(result['files']))

    def test_nonexecutable_xml_line_is_not_a_denominator_or_branch_union(self):
        self.source()
        reports = [self.report('compiled', [self.line(1, 0, 0, 7, 1), self.line(2, 3, 1, 8, 2)]),
                   self.report('client', [self.line(2, 0, 20, 1, 4)], '2222222222222222')]
        original = json.dumps(reports, sort_keys=True)
        result = self.evidence(reports)
        self.assertEqual([2], result['files'][0]['executableLines'])
        self.assertEqual(1, result['covered'])
        self.assertNotIn('BRANCH', result)
        self.assertNotIn('INSTRUCTION', result)
        self.assertEqual(original, json.dumps(reports, sort_keys=True))

    def test_dependency_host_even_with_project_source_name_is_never_credited(self):
        self.source()
        report = self.report('host', [self.line(1, 0, 10000)], owners={HOST: '1111111111111111'})
        result = self.evidence([report])
        self.assertEqual(0, result['executable'])
        self.assertEqual(['source_outside_project_inventory'], self.reasons(result))

    def test_missing_source_does_not_become_unexecuted_or_inflate_denominator(self):
        result = self.evidence([self.report('compiled', [self.line(1)])])
        self.assertEqual((0, 0, 0), (result['executable'], result['covered'], result['unexecuted']))
        self.assertFalse(result['mappingComplete'])
        self.assertEqual(['source_not_found'], self.reasons(result))

    def test_ambiguous_java_kotlin_source_is_reported_instead_of_arbitrarily_chosen(self):
        self.source()
        self.source(language='java')
        result = self.evidence([self.report('client', [self.line(1, 0, 1)])])
        self.assertEqual(0, result['executable'])
        self.assertEqual(['ambiguous_source'], self.reasons(result))
        self.assertEqual(2, len(result['anomalies'][0]['candidates']))

    def test_zero_negative_malformed_and_out_of_range_lines_are_excluded(self):
        self.source(lines=2)
        report = self.report('client', [self.line(1, 0, 1), self.line(3, 0, 100),
                             self.line(0, 0, 1), self.line(-1, 0, 1), self.line('oops'),
                             self.line(2, -1, 3), {'nr': '2', 'ci': '1'}])
        result = self.evidence([report])
        self.assertEqual((1, 1), (result['executable'], result['covered']))
        self.assertEqual(1, self.reasons(result).count('line_out_of_range'))
        self.assertEqual(5, self.reasons(result).count('invalid_line_mapping'))

    def test_physical_lines_handle_crlf_no_final_newline_and_unicode_separators(self):
        for data, lines in ((b'', 0), (b'one\r\ntwo\r\n', 2), (b'one\ntwo', 2),
                            (b'one\rtwo\r', 2), ('one\u2028two\n'.encode(), 1),
                            (b'\xef\xbb\xbf// BOM\n', 1)):
            with self.subTest(data=data):
                self.source(data=data)
                result = self.evidence([self.report('client', [self.line(lines, 0, 1), self.line(lines + 1)])])
                self.assertEqual(lines, result['files'][0]['physicalLineCount'])
                self.assertEqual(1 if lines else 0, result['covered'])

    def test_source_changed_after_freeze_is_an_anomaly_even_at_same_line_count(self):
        path = self.source(data=b'old\nold\n')
        frozen = coverage.freeze_sources(self.root)
        path.write_bytes(b'new\nnew\n')
        result = self.evidence([self.report('client', [self.line(1, 0, 1)])], frozen_sources=frozen)
        self.assertEqual(0, result['executable'])
        self.assertEqual(['source_changed_after_freeze'], self.reasons(result))

    def test_new_source_not_in_report_start_snapshot_is_not_credited(self):
        frozen = coverage.freeze_sources(self.root)
        self.source()
        result = self.evidence([self.report('client', [self.line(1, 0, 1)])], frozen_sources=frozen)
        self.assertEqual(0, result['executable'])
        self.assertEqual(['source_not_frozen'], self.reasons(result))

    def test_supplied_run_manifest_must_match_source_bytes(self):
        path = self.source()
        relative = path.relative_to(self.root).as_posix()
        report = self.report('client', [self.line(1, 0, 1)])
        for expected in ({}, {relative: '0' * 64}):
            with self.subTest(expected=expected):
                result = self.evidence([report], expected_sources=expected)
                self.assertEqual(0, result['covered'])
                self.assertEqual(['source_manifest_mismatch'], self.reasons(result))
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        result = self.evidence([report], expected_sources={relative: digest})
        self.assertEqual(1, result['covered'])
        self.assertEqual('supplied-manifest-and-report-start-sha256', result['sourceFreeze']['basis'])

    def test_source_manifest_format_rejects_invalid_hashes(self):
        for value in ([], {'source.kt': 'wrong'}, {'source.kt': None}):
            manifest = self.put('manifest.json', json.dumps(value).encode())
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, 'Source manifest must map'):
                coverage.load_source_manifest(manifest)
        manifest = self.put('manifest.json', json.dumps({'src.kt': 'A' * 64}).encode())
        self.assertEqual({'src.kt': 'a' * 64}, coverage.load_source_manifest(manifest))

    def test_source_path_escape_invalid_encoding_and_symlink_are_not_credited(self):
        for filename in ('../Widget.kt', '/Widget.kt', 'C:\\Widget.kt'):
            with self.subTest(filename=filename):
                result = self.evidence([self.report('client', [self.line(1, 0, 1)], filename=filename)])
                self.assertEqual(['invalid_source_path'], self.reasons(result))
        path = self.source(data=b'\xff\xfe')
        result = self.evidence([self.report('client', [self.line(1, 0, 1)])])
        self.assertEqual(['source_read_error'], self.reasons(result))
        path.unlink()
        with tempfile.TemporaryDirectory() as outside:
            target = Path(outside) / 'Widget.kt'
            target.write_bytes(b'outside\n')
            try:
                path.symlink_to(target)
            except OSError:
                # Creating symlinks can require additional rights on Windows.
                with patch.object(coverage.Path, 'resolve', autospec=True,
                                  side_effect=lambda candidate: target if candidate == path else candidate):
                    self.assertEqual({'error': 'source_outside_project'},
                                     coverage.source_metadata(path, self.root))
                return
            result = self.evidence([self.report('client', [self.line(1, 0, 1)])])
            self.assertEqual(['source_outside_project'], self.reasons(result))
            self.assertEqual(0, result['covered'])

    def test_duplicate_source_nodes_and_duplicate_line_numbers_do_not_inflate(self):
        self.source()
        report = self.report('client', [self.line(1, 0, 1), self.line(1), self.line(2, 0, 1)])
        result = self.evidence([report])
        self.assertEqual([2], result['files'][0]['coveredLines'])
        self.assertEqual(['duplicate_source_line'], self.reasons(result))
        report['sources'] *= 2
        result = self.evidence([report])
        self.assertEqual(0, result['covered'])
        self.assertEqual(['duplicate_source_mapping'], self.reasons(result))

    def test_source_cannot_borrow_an_unselected_or_mismatched_class_identity(self):
        self.source()
        report = self.report('client', [self.line(1, 0, 1)])
        report['classes'][WIDGET]['classId'] = '2222222222222222'
        result = self.evidence([report])
        self.assertEqual(0, result['covered'])
        self.assertEqual(['source_outside_project_inventory'], self.reasons(result))

    def test_xml_line_evidence_and_per_identity_branch_rows_remain_separate(self):
        self.source(lines=12)
        reports = []
        for index, (missed, covered, mb, cb) in enumerate(((20, 0, 8, 0), (5, 1, 1, 3))):
            identity = str(index + 1) * 16
            path = self.root / f'xml-{index}.xml'
            write_xml(path, {WIDGET: identity}, {(WIDGET, identity): {
                'INSTRUCTION': (missed, covered), 'BRANCH': (mb, cb), 'LINE': (1, 0)}})
            report = coverage.read_report(path, {WIDGET: identity})
            report.update(xml=path.name, classIds={WIDGET: identity})
            reports.append(report)
        before = json.dumps(reports, sort_keys=True)
        result = self.evidence(reports)
        self.assertEqual((1, 1, 0), (result['executable'], result['covered'], result['unexecuted']))
        self.assertEqual([12], result['files'][0]['coveredLines'])
        self.assertEqual(['8', '1'], [report['sources'][0]['lines'][0]['mb'] for report in reports])
        self.assertEqual(before, json.dumps(reports, sort_keys=True))

    def test_empty_evidence_is_zero_and_markdown_labels_union_limits(self):
        result = self.evidence([])
        self.assertEqual((0, 0, 0, 0), tuple(result[key] for key in
                         ('executable', 'covered', 'unexecuted', 'percent')))
        summary = {'counters': {}, 'sourceLineEvidence': result, 'identityReports': [],
                   'classIdentities': {}, 'unexecutedClasses': [], 'unreportedClasses': []}
        text = coverage.markdown(summary)
        self.assertIn('源码物理行执行证据', text)
        self.assertIn('不是跨身份的指令或分支覆盖率', text)
        self.assertIn('--source-manifest', text)
        self.assertIn('异常记录不进入', text)
        result['mappingComplete'] = False
        result['anomalies'] = [{'reason': 'source_not_found'}]
        self.assertIn('不完整，比例仅描述有效映射子集', coverage.markdown(summary))


class ForgeEventWrapperTests(FixtureCase):
    def test_forge_eventbus_wrappers_ignored_while_unknown_project_classes_fail(self):
        self.compiled()
        widget_exec = self.runtime('server', WIDGET, b'compiled-widget')
        self.runtime('server', PREFIX + '__EventHandler_onServerTick_ServerTickEvent', b'synthetic-bus-wrapper')
        baseline, project, hosts = coverage.collect_classes(self.root, [widget_exec])
        self.assertEqual([WIDGET], sorted(baseline))
        self.assertEqual([WIDGET], sorted(project))
        self.assertEqual({}, hosts)
        self.runtime('server', PREFIX + 'UnexpectedRuntimeClass', b'unexpected-project-class')
        with self.assertRaisesRegex(ValueError, 'UnexpectedRuntimeClass'):
            coverage.collect_classes(self.root, [widget_exec])


if __name__ == '__main__':
    unittest.main()
