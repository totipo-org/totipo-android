# totipo-android

Android client for Totipo.

This repository is currently in its initial bootstrap phase. The checked-in Nix flake provides the reproducible development environment and `jailed-codex` agent used to establish the Android project.

The Android application, Android SDK/toolchain definition, Gradle wrapper, build packaging, tests, and CI are intentionally not yet present. They will be established as the first implementation milestone after inspecting the existing `totipo-spec`, `totipo-java`, and `totipo-desktop` repositories.

## Bootstrap

Enter the development environment:

```sh
nix develop
```

Or, with direnv:

```sh
direnv allow
```

Launch the repository-local development agent:

```sh
jailed-codex
```
