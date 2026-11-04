import importlib.util, pathlib, unittest
spec=importlib.util.spec_from_file_location('ci_plan',pathlib.Path(__file__).with_name('ci-plan.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class ImpactTests(unittest.TestCase):
    def test_docs_cannot_skip_root(self):self.assertEqual(module.plan(['docs/demo-runbook.md','tasks/done/96.md']),['root'])
    def test_user_contract_checks_callers(self):self.assertTrue({'root','user','admin','notificator','payment','quiz','content','gateway'} <= set(module.plan(['services/erudit-user/src/main/resources/static/openapi.yaml'])))
    def test_platform_and_unknown_changes_are_conservative(self):
        for path in ['platform/erudit-integration/build.gradle.kts','build.gradle.kts','new-global-config']:
            self.assertEqual(module.plan([path]),module.ALL)
    def test_every_matrix_has_root_and_unique_gradle_paths(self):
        for names in [module.ALL,module.plan([]),module.plan(['services/erudit-quiz/src/main/java/Quiz.java'])]:
            rows=module.matrix(names)['include'];self.assertEqual(rows[0],{'id':'root','module':':'})
            self.assertEqual(len(rows),len({r['module'] for r in rows}))
if __name__=='__main__':unittest.main()
