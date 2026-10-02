"""Tests des outils de publication : check_versions.py, tag-plan.sh, deploy-server.sh.

Tous les dépôts git sont créés dans un répertoire temporaire (git init + commits à l'intérieur seulement) ; aucun accès réseau,
aucun serveur : un faux « ssh » qui échoue est placé en tête du PATH, et le script distant est joué en local avec de faux
« docker », « flock », « sleep » et « mv -T ».

    python3 -m unittest discover -s tools/tests -p 'test_release_tools.py'
"""
import importlib.util
import json
import os
import shutil
import stat
import subprocess
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
CHECK = os.path.join(REPO, "tools", "release", "check_versions.py")
TAGPLAN = os.path.join(REPO, "tools", "release", "tag-plan.sh")
DEPLOY = os.path.join(REPO, "tools", "release", "deploy-server.sh")

spec = importlib.util.spec_from_file_location("check_versions", CHECK)
cv = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cv)

PROPS = """# test : variante verrouillée +1 et -verrouillee
phone.versionName={phone}
phone.versionCode={phone_code}
lock.graceStartMs=1790899200000
lock.graceDays=30
tv.versionName={tv}
tv.versionCode={tv_code}
owner.versionName=0.2.3
owner.versionCode=5
dev.versionName=0.1.0
dev.versionCode=1
"""
GIT_ENV = dict(os.environ, GIT_AUTHOR_NAME="t", GIT_AUTHOR_EMAIL="t@example.org", GIT_COMMITTER_NAME="t",
               GIT_COMMITTER_EMAIL="t@example.org", GIT_CONFIG_GLOBAL="/dev/null", GIT_CONFIG_SYSTEM="/dev/null",
               GIT_CONFIG_NOSYSTEM="1")


def run(cmd, cwd=None, env=None, check=True, inp=None):
    p = subprocess.run(cmd, cwd=cwd, env=env or GIT_ENV, capture_output=True, text=True, input=inp)
    if check and p.returncode != 0:
        raise AssertionError("%s -> %d\n%s\n%s" % (cmd, p.returncode, p.stdout, p.stderr))
    return p


def write(path, text, mode=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    if mode:
        os.chmod(path, mode)


def props(phone="1.2.1-beta", phone_code=10, tv="0.1.0-beta", tv_code=20):
    return PROPS.format(phone=phone, phone_code=phone_code, tv=tv, tv_code=tv_code)


class TempRepo(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="cbrel-")
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.repo = os.path.join(self.tmp, "repo")
        os.makedirs(self.repo)
        self.git("init", "-q", "-b", "integration")

    def git(self, *args, **kw):
        return run(["git", "-C", self.repo] + list(args), **kw)

    def commit(self, text=None, msg="c", **files):
        if text is not None:
            write(os.path.join(self.repo, "version.properties"), text)
        for name, content in files.items():
            write(os.path.join(self.repo, name.replace("__", "/")), content)
        self.git("add", "-A")
        self.git("commit", "-q", "--allow-empty", "-m", msg)
        return self.git("rev-parse", "HEAD").stdout.strip()

    def check(self, *extra, root=None):
        return run(["python3", CHECK, "--root", root or self.repo] + list(extra), check=False)


class ParsingTests(unittest.TestCase):
    def test_parse_properties(self):
        p, bad, dup = cv.parse_properties("# c\na.b=1\n\nc=2\nc=3\nnoequal\n9x=1\n")
        self.assertEqual(p["a.b"], "1")
        self.assertEqual(dup, ["c"])
        self.assertEqual(len(bad), 2)

    def test_semver_order(self):
        self.assertLess(cv.semver_key("1.2.9-beta"), cv.semver_key("1.2.10-beta"))
        self.assertLess(cv.semver_key("1.2.9-beta"), cv.semver_key("1.2.9"))
        self.assertIsNone(cv.semver_key("1.2-beta"))
        self.assertIsNone(cv.semver_key("1.2.3-rc1"))


class CheckVersionsTests(TempRepo):
    def levels(self, text_or_json):
        return [(i["controle"], i["niveau"]) for i in json.loads(text_or_json)["controles"]]

    def messages(self, out, level):
        return [i["message"] for i in json.loads(out)["controles"] if i["niveau"] == level]

    def test_clean_repo_ok(self):
        self.commit(props())
        p = self.check("--json")
        self.assertEqual(p.returncode, 0, p.stdout)
        self.assertEqual(json.loads(p.stdout)["erreurs"], 0)

    def test_not_a_repo_is_exit_2(self):
        d = os.path.join(self.tmp, "nogit")
        write(os.path.join(d, "version.properties"), props())
        self.assertEqual(self.check(root=d).returncode, 2)
        self.assertEqual(self.check(root=os.path.join(self.tmp, "absent")).returncode, 2)

    def test_malformed_and_semver(self):
        self.commit(props(phone="1.2"))
        p = self.check("--json")
        self.assertEqual(p.returncode, 1)
        self.assertTrue(any("phone.versionName" in m for m in self.messages(p.stdout, "erreur")))

    def test_code_changed_without_name(self):
        self.commit(props())
        write(os.path.join(self.repo, "version.properties"), props(phone_code=11))
        p = self.check("--json")
        self.assertEqual(p.returncode, 1)
        self.assertTrue(any("sans changer le nom" in m for m in self.messages(p.stdout, "erreur")))

    def test_code_not_increasing(self):
        self.commit(props())
        write(os.path.join(self.repo, "version.properties"), props(phone="1.2.2-beta", phone_code=10))
        p = self.check("--json")
        self.assertEqual(p.returncode, 1)

    def test_tv_needs_plus_two_for_locked_variant(self):
        self.commit(props())
        write(os.path.join(self.repo, "version.properties"), props(tv="0.1.1-beta", tv_code=21))
        p = self.check("--json")
        self.assertEqual(p.returncode, 1)
        self.assertTrue(any("attendu >= 22" in m for m in self.messages(p.stdout, "erreur")))
        write(os.path.join(self.repo, "version.properties"), props(tv="0.1.1-beta", tv_code=22))
        self.assertEqual(self.check("--json").returncode, 0)

    def test_downgrade_name(self):
        self.commit(props(phone="1.2.5-beta", phone_code=10))
        write(os.path.join(self.repo, "version.properties"), props(phone="1.2.4-beta", phone_code=11))
        p = self.check("--json")
        self.assertTrue(any("rétrogradée" in m for m in self.messages(p.stdout, "erreur")))

    def test_against_tags(self):
        self.commit(props())
        self.git("tag", "-a", "phone-1.2.1-beta", "-m", "t")
        self.git("tag", "-a", "tv-0.1.0-beta", "-m", "t")
        # même nom mais autre code que le tag
        self.commit(props(phone_code=99), msg="x")
        p = self.check("--json")
        self.assertTrue(any("phone-1.2.1-beta" in m for m in self.messages(p.stdout, "erreur")))
        # nouveau nom avec code <= code du tag
        self.commit(props(phone="1.2.2-beta", phone_code=10), msg="y")
        p = self.check("--json")
        self.assertTrue(any("non strictement croissant" in m for m in self.messages(p.stdout, "erreur")))
        # TV : le code du tag + 1 est réservé à la variante verrouillée
        self.commit(props(phone="1.2.3-beta", phone_code=11, tv="0.1.1-beta", tv_code=21), msg="z")
        p = self.check("--json")
        self.assertTrue(any("tv-0.1.0-beta" in m for m in self.messages(p.stdout, "erreur")) or
                        any("tv" in m for m in self.messages(p.stdout, "erreur")), p.stdout)

    def test_no_tags_warns(self):
        self.commit(props())
        p = self.check("--json")
        self.assertTrue(any("aucun tag" in m for m in self.messages(p.stdout, "avert")))
        self.assertEqual(self.check("--strict").returncode, 1)

    def test_head_tagged(self):
        self.commit(props())
        self.git("tag", "-a", "tv-0.1.0-beta", "-m", "t")
        p = self.check("--json")
        self.assertIn("tv-0.1.0-beta", json.loads(p.stdout)["git"]["tags_head"])

    def test_git_state_dirty_and_untracked(self):
        self.commit(props())
        write(os.path.join(self.repo, "new.txt"), "x")
        write(os.path.join(self.repo, "version.properties"), props() + "# edit\n")
        st = json.loads(self.check("--json").stdout)["git"]
        self.assertEqual((st["modifies"], st["non_suivis"]), (1, 1))
        self.assertEqual(st["branche"], "integration")

    def test_ahead_behind_origin(self):
        self.commit(props())
        origin = os.path.join(self.tmp, "origin.git")
        run(["git", "init", "-q", "--bare", origin])
        self.git("remote", "add", "origin", origin)
        self.git("push", "-q", "origin", "integration")
        self.git("fetch", "-q", "origin")
        self.commit(props(), msg="local")
        st = json.loads(self.check("--json").stdout)["git"]
        self.assertEqual((st["avance"], st["retard"]), (1, 0))

    def test_server_lines_vs_pom(self):
        pom = "<project><parent><version>3.5.16</version></parent><version>1.0.0</version></project>"
        self.commit(props(), backend__pom_xml="x")
        write(os.path.join(self.repo, "backend", "pom.xml"), pom)
        self.assertEqual(cv.pom_version(os.path.join(self.repo, "backend", "pom.xml")), "1.0.0")
        p = self.check("--json")
        self.assertTrue(any("server.versionName=1.0.0" in m for m in self.messages(p.stdout, "avert")))
        self.commit(props() + "server.versionName=1.0.1\nserver.versionCode=1\n", msg="s")
        p = self.check("--json")
        self.assertTrue(any("différent de backend/pom.xml" in m for m in self.messages(p.stdout, "erreur")))
        self.commit(props() + "server.versionName=1.0.0\nserver.versionCode=1\n", msg="s2")
        self.assertEqual(self.check("--json").returncode, 0)
        self.commit(props() + "server.versionName=1.0.0\n", msg="s3")
        self.assertEqual(self.check("--json").returncode, 1)

    def test_locked_rule_documentation(self):
        gradle = 'versionCode = x + if (locked) 1 else 0\nversionName = y + if (locked) "-verrouillee" else ""\n'
        text = props() + "# variante verrouillée : +1 et -verrouillee\n"
        self.commit(text, docs__RELEASES_md="Code : sans verrou = 60, verrouillée = 61 ; suffixe -verrouillee, +1\n",
                    android__receiver__build_gradle_kts=gradle)
        # le répertoire « android/receiver/build.gradle.kts » : les « __ » remplacent « / », les « _ » restent
        write(os.path.join(self.repo, "android", "receiver", "build.gradle.kts"), gradle)
        write(os.path.join(self.repo, "docs", "RELEASES.md"), "sans verrou = 60, verrouillée = 61 ; -verrouillee +1\n")
        p = self.check("--json")
        self.assertFalse([m for m in self.messages(p.stdout, "erreur") if "verrou" in m], p.stdout)
        write(os.path.join(self.repo, "docs", "RELEASES.md"), "sans verrou = 60, verrouillée = 62 ; -verrouillee +1\n")
        p = self.check("--json")
        self.assertTrue([m for m in self.messages(p.stdout, "erreur") if "attendu +1" in m])
        write(os.path.join(self.repo, "docs", "RELEASES.md"), "suffixe -verrouille +1\n")
        p = self.check("--json")
        self.assertTrue([m for m in self.messages(p.stdout, "erreur") if "suffixe incohérent" in m])

    def fake_aapt2(self, package, name, code):
        path = os.path.join(self.tmp, "aapt2")
        write(path, "#!/bin/sh\necho \"package: name='%s' versionCode='%s' versionName='%s' compileSdkVersion='34'\"\n" % (package, code, name),
              0o755)
        return path

    def test_apk_match_and_mismatch(self):
        self.commit(props(tv="0.1.0-beta", tv_code=20))
        apk = os.path.join(self.tmp, "receiver-armeabi-v7a-release.apk")
        write(apk, "x")
        env = dict(GIT_ENV)
        for name, code, ok in (("0.1.0-beta", 20, True), ("0.1.0-beta-verrouillee", 21, True),
                               ("0.1.0-beta-verrouillee", 20, False), ("0.1.1-beta", 22, False)):
            env["CASTBRIDGE_AAPT2"] = self.fake_aapt2("castbridge.receiver", name, code)
            p = run(["python3", CHECK, "--root", self.repo, "--json", "--apk", apk], env=env, check=False)
            self.assertEqual(p.returncode, 0 if ok else 1, (name, code, p.stdout))

    def test_apk_without_aapt2(self):
        self.commit(props())
        apk = os.path.join(self.tmp, "sender.apk")
        write(apk, "x")
        env = dict(GIT_ENV, CASTBRIDGE_AAPT2=os.path.join(self.tmp, "absent"))
        p = run(["python3", CHECK, "--root", self.repo, "--json", "--apk", apk], env=env, check=False)
        self.assertTrue(any("aapt2 introuvable" in m for m in self.messages(p.stdout, "avert")))

    def test_text_output_is_french(self):
        self.commit(props())
        out = self.check().stdout
        self.assertIn("Contrôle des versions CastBridge", out)
        self.assertIn("Bilan :", out)


class TagPlanTests(TempRepo):
    def plan(self, *args):
        env = dict(GIT_ENV, CB_REPO=self.repo)
        return run(["bash", TAGPLAN] + list(args), env=env, check=False)

    def build_history(self):
        self.c1 = self.commit(props(phone="1.0.0-beta", phone_code=1, tv="0.1.0-beta", tv_code=1), msg="v1")
        self.c2 = self.commit(props(phone="1.0.1-beta", phone_code=2, tv="0.1.0-beta", tv_code=1), msg="v2")
        self.c3 = self.commit(props(phone="1.0.2-beta", phone_code=3, tv="0.1.1-beta", tv_code=3), msg="v3")

    def test_proven_versions_dry_run(self):
        self.build_history()
        p = self.plan()
        self.assertEqual(p.returncode, 0, p.stderr)
        self.assertIn("git tag -a phone-1.0.0-beta %s" % self.c1, p.stdout)
        self.assertIn("git tag -a phone-1.0.2-beta %s" % self.c3, p.stdout)
        self.assertIn("git tag -a tv-0.1.0-beta %s" % self.c1, p.stdout)
        self.assertIn("git tag -a tv-0.1.1-beta %s" % self.c3, p.stdout)
        self.assertIn("git push origin refs/tags/tv-0.1.1-beta", p.stdout)
        self.assertIn("DRY-RUN", p.stdout)
        self.assertEqual(self.git("tag", "-l").stdout.strip(), "", "le dry-run ne doit rien créer")

    def test_reintroduced_version_is_ambiguous(self):
        self.build_history()
        self.commit(props(phone="1.0.1-beta", phone_code=4, tv="0.1.1-beta", tv_code=3), msg="back")
        p = self.plan()
        self.assertIn("phone-1.0.1-beta : introduite par 2 commits", p.stdout)
        self.assertNotIn("git tag -a phone-1.0.1-beta", p.stdout)

    def test_working_tree_only_is_ambiguous(self):
        self.build_history()
        write(os.path.join(self.repo, "version.properties"), props(phone="1.0.9-beta", phone_code=9, tv="0.1.1-beta", tv_code=3))
        p = self.plan()
        self.assertIn("phone-1.0.9-beta : présente seulement dans l'arbre de travail", p.stdout)

    def test_gap_is_reported(self):
        self.commit(props(phone="1.0.0-beta", phone_code=1, tv="0.1.0-beta", tv_code=1), msg="v1")
        self.commit(props(phone="1.0.3-beta", phone_code=7, tv="0.1.0-beta", tv_code=1), msg="v2")
        self.assertIn("intermédiaires", self.plan().stdout)

    def test_apply_creates_local_tags_only_and_never_moves(self):
        self.build_history()
        origin = os.path.join(self.tmp, "origin.git")
        run(["git", "init", "-q", "--bare", origin])
        self.git("remote", "add", "origin", origin)
        self.git("tag", "-a", "phone-1.0.0-beta", self.c3, "-m", "déjà là, sur un autre commit")
        p = self.plan("--apply")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        tags = self.git("tag", "-l").stdout.split()
        self.assertIn("tv-0.1.1-beta", tags)
        self.assertEqual(self.git("rev-list", "-n", "1", "phone-1.0.0-beta").stdout.strip(), self.c3, "tag existant déplacé !")
        self.assertIn("NON déplacé", p.stdout)
        self.assertEqual(self.git("rev-list", "-n", "1", "tv-0.1.1-beta").stdout.strip(), self.c3)
        self.assertEqual(run(["git", "-C", origin, "tag", "-l"]).stdout.strip(), "", "aucun push sans --push")
        # annoté
        self.assertEqual(self.git("cat-file", "-t", "tv-0.1.1-beta").stdout.strip(), "tag")
        # --push publie
        p = self.plan("--apply", "--push")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        self.assertIn("tv-0.1.1-beta", run(["git", "-C", origin, "tag", "-l"]).stdout)

    def test_refuses_apply_on_main(self):
        self.build_history()
        self.git("checkout", "-q", "-b", "main")
        p = self.plan("--apply")
        self.assertEqual(p.returncode, 2)
        self.assertEqual(self.git("tag", "-l").stdout.strip(), "")

    def test_unknown_option(self):
        self.build_history()
        self.assertEqual(self.plan("--oops").returncode, 2)

    def test_bash_syntax(self):
        run(["bash", "-n", TAGPLAN])


class DeployServerTests(TempRepo):
    def setUp(self):
        super().setUp()
        self.bin = os.path.join(self.tmp, "bin")
        os.makedirs(self.bin)
        write(os.path.join(self.bin, "ssh"), "#!/bin/sh\necho 'SSH INTERDIT EN TEST' >&2\nexit 99\n", 0o755)
        write(os.path.join(self.bin, "scp"), "#!/bin/sh\nexit 99\n", 0o755)
        write(os.path.join(self.repo, "backend", "backup.sh"), "#!/bin/sh\nexit 0\n", 0o755)
        self.sha = self.commit(props(), msg="v", backend__Dockerfile="FROM scratch\n")
        self.git("tag", "-a", "server-1.0.1", "-m", "t")
        self.origin = os.path.join(self.tmp, "origin.git")
        run(["git", "init", "-q", "--bare", self.origin])
        self.git("remote", "add", "origin", self.origin)
        self.git("push", "-q", "origin", "integration")
        self.git("fetch", "-q", "origin")
        # « bridge » : dépôt bare de production (ici local ; le contrôle d'hôte est neutralisé pour les tests)
        self.bridge = os.path.join(self.tmp, "bridge.git")
        run(["git", "init", "-q", "--bare", self.bridge])
        self.git("remote", "add", "bridge", self.bridge)

    def deploy(self, *args, env_extra=None):
        env = dict(GIT_ENV, CB_REPO=self.repo, PATH=self.bin + os.pathsep + os.environ["PATH"], CB_BRIDGE_HOST_CHECK="0")
        env.update(env_extra or {})
        return run(["bash", DEPLOY] + list(args), env=env, check=False)

    def bridge_refs(self):
        return run(["git", "-C", self.bridge, "for-each-ref", "--format=%(refname)"]).stdout.split()

    def test_help_and_syntax(self):
        run(["bash", "-n", DEPLOY])
        p = self.deploy("--help")
        self.assertEqual(p.returncode, 0)
        self.assertIn("DRY-RUN", p.stdout)
        self.assertIn("castbridge.git", p.stdout)
        self.assertNotIn("set -Eeuo", p.stdout)

    def test_requires_ref(self):
        self.assertEqual(self.deploy().returncode, 2)

    def test_dry_run_prints_plan_with_labels_and_no_connection(self):
        p = self.deploy("server-1.0.1")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        out = p.stdout
        self.assertIn("server-1.0.1 (tag) -> %s (1.0.1)" % self.sha, out)
        self.assertIn("/home/ubuntu/castbridge/releases/server-1.0.1-", out)
        self.assertIn("ubuntu@bridge.sti-cm.com", out)
        self.assertIn("git push bridge refs/tags/server-1.0.1:refs/tags/server-1.0.1", out)
        self.assertIn("git --git-dir=\"$BARE\" archive", out)
        self.assertIn("org.opencontainers.image.revision", out)
        self.assertIn("compose --project-name castbridge", out)
        self.assertIn("backup.sh --db-only", out)
        self.assertIn("castbridge-api:previous", out)
        self.assertNotIn("BLOQUANT", out)
        self.assertNotIn("SSH INTERDIT", p.stdout + p.stderr)
        self.assertEqual(self.bridge_refs(), [], "le dry-run ne doit rien pousser")
        script = out.split("--- script distant", 1)[1]
        for forbidden in ("nginx", "certbot", "docker stop", "docker rm ", "down", "restart"):
            self.assertNotIn(forbidden, script.replace("rmi castbridge-api:candidate", ""), forbidden)

    def test_dry_run_with_fake_ref_still_prints_plan(self):
        p = self.deploy("server-9.9.9")
        self.assertEqual(p.returncode, 0)
        self.assertIn("révision introuvable", p.stdout)
        self.assertIn("script distant", p.stdout)

    def test_apply_refused_for_fake_ref_dirty_tree_unpushed_and_bare_commit(self):
        p = self.deploy("server-9.9.9", "--apply")
        self.assertEqual(p.returncode, 3)
        self.assertNotIn("SSH INTERDIT", p.stderr)
        write(os.path.join(self.repo, "version.properties"), props(phone_code=77))
        p = self.deploy("server-1.0.1", "--apply")
        self.assertEqual(p.returncode, 3)
        self.assertIn("arbre de travail modifié", p.stdout)
        self.git("checkout", "-q", "--", "version.properties")
        local = self.commit(props(phone="1.2.2-beta", phone_code=11), msg="local only")
        p = self.deploy(local, "--apply")
        self.assertEqual(p.returncode, 3)
        self.assertIn("atteignable depuis aucune branche origin", p.stdout)
        self.assertIn("n'est ni un tag ni une branche", p.stdout)
        self.assertEqual(self.bridge_refs(), [])
        self.assertNotIn("SSH INTERDIT", p.stderr)

    def test_main_is_never_pushed(self):
        self.git("branch", "main")
        p = self.deploy("main", "--apply")
        self.assertEqual(p.returncode, 3)
        self.assertIn("refus de déployer/pousser", p.stdout)
        self.assertEqual(self.bridge_refs(), [])

    def test_missing_or_wrong_bridge_remote_blocks(self):
        self.git("remote", "remove", "bridge")
        p = self.deploy("server-1.0.1", "--apply")
        self.assertEqual(p.returncode, 3)
        self.assertIn("remote git « bridge » absent", p.stdout)
        self.git("remote", "add", "bridge", self.bridge)
        p = self.deploy("server-1.0.1", "--apply", env_extra={"CB_BRIDGE_HOST_CHECK": "1"})
        self.assertEqual(p.returncode, 3)
        self.assertIn("ne pointe pas sur bridge.sti-cm.com", p.stdout)

    def test_push_only_pushes_one_tag_never_moves_it(self):
        p = self.deploy("server-1.0.1", "--push-only", "--apply")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        self.assertEqual(self.bridge_refs(), ["refs/tags/server-1.0.1"])
        p = self.deploy("server-1.0.1", "--push-only", "--apply")
        self.assertEqual(p.returncode, 0)
        self.assertIn("déjà présent", p.stdout + p.stderr)
        # le tag local est déplacé sur un autre commit : le script doit refuser de l'écraser sur le serveur
        other = self.commit(props(phone="1.2.2-beta", phone_code=11), msg="other")
        self.git("push", "-q", "origin", "integration")
        self.git("fetch", "-q", "origin")
        self.git("tag", "-f", "-a", "server-1.0.1", "-m", "moved", other)
        p = self.deploy("server-1.0.1", "--push-only", "--apply")
        self.assertEqual(p.returncode, 1)
        self.assertIn("refus de le déplacer", p.stderr)
        self.assertEqual(run(["git", "-C", self.bridge, "rev-parse", "refs/tags/server-1.0.1^{commit}"]).stdout.strip(), self.sha)

    def test_untracked_files_only_block_in_strict_mode(self):
        write(os.path.join(self.repo, "heavy.bin"), "x")
        self.assertNotIn("BLOQUANT", self.deploy("server-1.0.1").stdout)
        self.assertIn("non suivi", self.deploy("server-1.0.1", "--strict-clean").stdout)

    def test_status_and_rollback_dry_run(self):
        for flag in ("--status", "--rollback"):
            p = self.deploy(flag)
            self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
            self.assertIn("DRY-RUN", p.stdout)
            self.assertNotIn("SSH INTERDIT", p.stdout + p.stderr)
        st = self.deploy("--status").stdout
        self.assertIn("org.opencontainers.image.revision", st)
        self.assertIn("for-each-ref", st)

    # -- le script distant joué en local avec de faux outils -----------------------------------------------------------
    def sandbox(self):
        sandbox = os.path.join(self.tmp, "sb")
        shims = os.path.join(sandbox, "shims")
        os.makedirs(shims)
        log = os.path.join(sandbox, "docker.log")
        write(os.path.join(shims, "docker"),
              '#!/bin/bash\necho "docker $*" >> "%s"\n'
              'case "$*" in\n'
              '  *"image inspect -f"*image.revision*) echo "$FAKE_SHA";;\n'
              '  *"image inspect -f"*) echo "sha256:fake";;\n'
              '  *"inspect -f {{.State.Running}}"*) echo true;;\n'
              '  *"inspect -f {{if .State.Health}}"*) echo "${FAKE_HEALTH:-healthy}";;\n'
              '  *"inspect -f"*image.revision*) echo "$FAKE_SHA";;\n'
              'esac\nexit 0\n' % log, 0o755)
        write(os.path.join(shims, "flock"), "#!/bin/sh\nexit 0\n", 0o755)
        write(os.path.join(shims, "sleep"), "#!/bin/sh\nexit 0\n", 0o755)
        write(os.path.join(shims, "mv"), '#!/bin/bash\nargs=(); for a in "$@"; do [ "$a" = -T ] || args+=("$a"); done\nexec /bin/mv "${args[@]}"\n', 0o755)
        write(os.path.join(shims, "readlink"),
              '#!/bin/bash\nif [ "$1" = -f ]; then python3 -c "import os,sys;print(os.path.realpath(sys.argv[1]))" "$2"; else exec /usr/bin/readlink "$@"; fi\n', 0o755)
        # une « release en service » d'origine : disposition non git, avec ses secrets
        root = os.path.join(sandbox, "castbridge")
        live = os.path.join(root, "services", "castbridge", "backend")
        write(os.path.join(live, ".env"), "CASTBRIDGE_DB_PASSWORD=pas-un-vrai-secret\n", 0o644)
        write(os.path.join(live, "secrets", "castbridge-signing.pem"), "fake\n", 0o600)
        write(os.path.join(live, "docker-compose.override.yml"), "services: {}\n")
        write(os.path.join(live, "geoip", "db.mmdb"), "g")
        return shims, root, live, log

    def release_tag(self, tag, backup_exit=0):
        """Commit un backend avec un backup.sh donné, pousse tout vers « bridge » (bare local) et retourne le sha."""
        write(os.path.join(self.repo, "backend", "backup.sh"), "#!/bin/sh\nexit %d\n" % backup_exit, 0o755)
        sha = self.commit(None, msg="release " + tag)
        self.git("tag", "-a", tag, "-m", tag)
        self.git("push", "-q", "bridge", "refs/tags/%s:refs/tags/%s" % (tag, tag))
        return sha

    def run_remote(self, tag="server-1.0.2", health="healthy", backup_exit=0, prepare=None, tag_in_bare=True):
        sha = self.release_tag(tag, backup_exit)
        shims, root, live, log = self.sandbox()
        env = dict(GIT_ENV, CB_REPO=self.repo, PATH=self.bin + os.pathsep + os.environ["PATH"], CB_DEPLOY_ROOT=root,
                   CB_LIVE_DIR=live, CB_BARE_REPO=self.bridge, CB_DOCKER="docker", CB_HEALTH_TIMEOUT="30", CB_BRIDGE_HOST_CHECK="0")
        out = run(["bash", DEPLOY, tag], env=env).stdout
        script = out.split("--- script distant", 1)[1].split("\n", 1)[1].rsplit("--- fin ---", 1)[0]
        rel = [l.split("=", 1)[1] for l in script.splitlines() if l.startswith("REL=")][0]
        if prepare:
            prepare(root, live)
        renv = dict(os.environ, PATH=shims + os.pathsep + os.environ["PATH"], FAKE_SHA=sha, FAKE_HEALTH=health)
        p = subprocess.run(["bash", "-s"], input=script, capture_output=True, text=True, env=renv)
        return p, root, live, rel, (open(log).read() if os.path.exists(log) else ""), sha

    def test_remote_success_switches_symlinks_and_traces(self):
        p, root, live, rel, log, sha = self.run_remote()
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        self.assertTrue(os.path.basename(rel).startswith("server-1.0.2-"))
        self.assertEqual(os.path.realpath(os.path.join(root, "current")), os.path.realpath(rel))
        self.assertEqual(os.path.realpath(os.path.join(root, "previous")), os.path.realpath(live))
        self.assertEqual(open(os.path.join(rel, "REVISION")).read().strip(), sha)
        self.assertIn("sha=%s" % sha, open(os.path.join(rel, "RELEASE")).read())
        self.assertIn("version=1.0.2", open(os.path.join(rel, "RELEASE")).read())
        self.assertTrue(os.path.isfile(os.path.join(rel, "Dockerfile")), "release extraite du dépôt bare")
        self.assertFalse(os.path.exists(os.path.join(rel, "version.properties")), "seul backend/ doit être extrait")
        self.assertEqual(stat.S_IMODE(os.stat(os.path.join(rel, ".env")).st_mode), 0o600)
        self.assertTrue(os.path.isfile(os.path.join(rel, "secrets", "castbridge-signing.pem")))
        self.assertTrue(os.path.isfile(os.path.join(rel, "docker-compose.override.yml")))
        self.assertTrue(os.path.isfile(os.path.join(rel, "geoip", "db.mmdb")))
        self.assertTrue(os.path.isfile(os.path.join(root, "releases", "INITIAL-REVISION-INCONNUE.txt")))
        self.assertIn("--label org.opencontainers.image.revision=%s" % sha, log)
        self.assertIn("--label org.opencontainers.image.version=1.0.2", log)
        self.assertIn("--build-arg VCS_REF=%s" % sha, log)
        self.assertIn("tag castbridge-api:current castbridge-api:previous", log)
        self.assertIn("compose --project-name castbridge", log)
        self.assertIn("up -d --no-build --no-deps castbridge-api", log)
        self.assertNotIn("nginx", log)
        self.assertNotIn("sti-", log)
        self.assertTrue(os.path.isdir(live), "la release d'origine doit être COPIÉE, jamais déplacée")
        self.assertTrue(os.path.isfile(os.path.join(live, ".env")))
        self.assertEqual(stat.S_IMODE(os.stat(os.path.join(live, ".env")).st_mode), 0o644, "la source n'est pas modifiée")

    def test_remote_unhealthy_rolls_back_and_keeps_current_link(self):
        p, root, live, rel, log, sha = self.run_remote(health="unhealthy")
        self.assertEqual(p.returncode, 1, p.stdout + p.stderr)
        self.assertFalse(os.path.lexists(os.path.join(root, "current")), "le lien current ne doit pas basculer sur échec")
        self.assertIn("retour automatique", p.stdout)
        after_up = log.split("up -d --no-build --no-deps castbridge-api", 1)[1]
        self.assertIn("tag castbridge-api:previous castbridge-api:current", after_up)
        self.assertIn("up -d --no-build --no-deps castbridge-api", after_up, "l'ancienne version doit être redémarrée")
        self.assertIn(live, after_up, "le retour utilise les fichiers de la release précédente")
        self.assertNotIn("nginx", log)

    def test_remote_backup_failure_aborts_before_touching_service(self):
        p, root, live, rel, log, sha = self.run_remote(backup_exit=1)
        self.assertEqual(p.returncode, 1, p.stdout + p.stderr)
        self.assertNotIn("up -d", log)
        self.assertNotIn("tag castbridge-api:candidate castbridge-api:current", log)
        self.assertFalse(os.path.lexists(os.path.join(root, "current")))

    def test_remote_missing_env_aborts_without_build(self):
        p, root, live, rel, log, sha = self.run_remote(prepare=lambda root, live: os.remove(os.path.join(live, ".env")))
        self.assertEqual(p.returncode, 1)
        self.assertNotIn("build", log)
        self.assertFalse(os.path.exists(rel))

    def test_remote_unknown_commit_in_bare_aborts(self):
        sha = self.release_tag("server-1.0.3")
        shims, root, live, log = self.sandbox()
        empty = os.path.join(self.tmp, "empty.git")
        run(["git", "init", "-q", "--bare", empty])
        env = dict(GIT_ENV, CB_REPO=self.repo, PATH=self.bin + os.pathsep + os.environ["PATH"], CB_DEPLOY_ROOT=root,
                   CB_LIVE_DIR=live, CB_BARE_REPO=empty, CB_DOCKER="docker", CB_BRIDGE_HOST_CHECK="0")
        out = run(["bash", DEPLOY, "server-1.0.3"], env=env).stdout
        script = out.split("--- script distant", 1)[1].split("\n", 1)[1].rsplit("--- fin ---", 1)[0]
        renv = dict(os.environ, PATH=shims + os.pathsep + os.environ["PATH"], FAKE_SHA=sha)
        p = subprocess.run(["bash", "-s"], input=script, capture_output=True, text=True, env=renv)
        self.assertEqual(p.returncode, 1)
        self.assertIn("absente du dépôt bare", p.stdout)
        self.assertFalse(os.path.exists(log), "aucun appel docker avant l'abandon")


if __name__ == "__main__":
    unittest.main()
