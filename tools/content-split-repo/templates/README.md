# castbridge-content (private)

The training database of **CastBridge** (phone) and **CastBridge-TV** (TV): lessons (Apprendre), question banks (Quiz), the
curriculum skill graph and the media, organised in **lots**. Built in this repository by the content agents; **never produced on
the server**: the finished tree (`tools/publish-content.sh`) is copied to the production server by the owner.

- `content/learn/<pack>/` lessons and exercises (JSON), `content/quiz/dist/` quiz packs, `content/graph/` skill graph and scopes,
  `content/MEDIA-MANIFEST.json` every media asset (provenance, licence, hashes), `content/LOT-VERSIONS.json` lot versions.
- `tools/` content tools (budget, media check, lots, release, mapping, QA report validator).
- Git LFS holds heavy media (see `.gitattributes`). `git lfs install` before cloning.
- The Kotlin validators live in the CODE repository (`castbridge`), pinned by `CODE-REF`: set `CASTBRIDGE_CODE_DIR` to a checkout
  of that commit before running `tools/publish-content.sh`.
- Documents: `docs/CONTENT-ARCHITECTURE.md`, `CONTENT-PUBLISH.md`, `MEDIA-POLICY.md`, `PEDAGOGY-RUBRIC.md` (copied from the code repository).
Ceilings: TV lot 3 MB, learner path on the phone 100 MB, one file 50 MB, whole database 3 GB.
