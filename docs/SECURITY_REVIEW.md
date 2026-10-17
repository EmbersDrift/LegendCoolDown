# Pre-upload sensitive information review

Review date: 2026-10-17. Version: 1.1.0.

## Scope and findings

- Gitleaks 8.30.1 scanned the staged source code, build scripts, test fixtures, documentation, and extracted contents of all three release JARs using its default rules and decoding checks. No credential leaks were detected.
- Supplementary checks looked for GitHub tokens, AWS access keys, private keys, JWTs, URLs containing passwords, personal email addresses, and local user paths. No sensitive content was found in the upload set.
- PNG screenshots have no text, EXIF, or personal-path metadata. They show isolated local test worlds and the mod's interfaces.
- The two test fixtures retain only cooldown text, item names, and seconds. Original diagnostic filenames were removed from 1,425 records; all 75 unit tests still passed.
- Git commits use a GitHub noreply author email. The complete Git history was scanned after repository initialization and reviewed again before public publication.

## Files excluded from publication

The source repository excludes local Gradle caches, build outputs, game runtime directories, account sessions, logs, diagnostic exports, world saves, environment files, certificates, and private keys. The relevant paths and file types are listed in `.gitignore`.

Release JARs and SHA-256 checksums are distributed through GitHub Releases. The repository was initially uploaded privately, then made public at the maintainer's request after source and history scans.

## Repeat the history scan

```sh
gitleaks git . --redact --max-decode-depth 3
```

Automated scans identify known secret formats and common credential patterns; they cannot prove that every possible kind of sensitive information is absent. Runtime cooldown diagnostic reports may contain server addresses and inventory details and should remain excluded from version control.
