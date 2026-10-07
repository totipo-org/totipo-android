{ lib, stdenv, jdk, gradle, androidSdk, python3 }:
let
  version = lib.removeSuffix "\n" (builtins.readFile ./VERSION);
  cache = builtins.fromJSON (builtins.readFile ./package-deps.json);
  # fetchDeps compresses common URL prefixes according to the recorded graph.
  # Android spans com/org groups; desktop's smaller cache used an /org prefix.
  central = cache."https://repo.maven.apache.org/maven2" or { };
  centralOrg = cache."https://repo.maven.apache.org/maven2/org" or { };
  core = central."org/totipo#totipo-core/0.1.3"
    or (centralOrg."totipo#totipo-core/0.1.3" or { });
  bc = central."org/bouncycastle#bcprov-jdk18on/1.86"
    or (centralOrg."bouncycastle#bcprov-jdk18on/1.86" or { });
  cacheReady = core ? jar && (core ? module || core ? pom) && bc ? jar;
  trees = [ "app/src" "gradle" "tools" ];
  files = [
    "build.gradle.kts" "settings.gradle.kts" "gradle.properties"
    "app/build.gradle.kts" "app/gradle.lockfile"
    "buildscript-gradle.lockfile" "app/buildscript-gradle.lockfile"
    "VERSION" "LICENSE"
  ];
in
assert builtins.match "[0-9]+\\.[0-9]+\\.[0-9]+(-[A-Za-z0-9.-]+)?" version != null;
assert gradle.version == "9.8.0";
stdenv.mkDerivation (finalAttrs: {
  pname = "totipo-android";
  inherit version;
  src = lib.cleanSourceWith {
    src = ./.;
    filter = path: type:
      let
        rel = lib.removePrefix (toString ./. + "/") (toString path);
        parts = lib.splitString "/" rel;
        excluded = builtins.any (p: builtins.elem p [ ".git" ".gradle" "build" ".direnv" ]) parts;
        selected = builtins.elem rel files
          || builtins.any (tree: rel == tree || lib.hasPrefix (tree + "/") rel) trees
          || (type == "directory" && builtins.any (entry: lib.hasPrefix (rel + "/") entry) (trees ++ files));
      in !excluded && type != "symlink" && selected;
  };
  mitmCache = gradle.fetchDeps {
    pkg = finalAttrs.finalPackage;
    data = ./package-deps.json;
  };
  nativeBuildInputs = [ gradle python3 ];
  JAVA_HOME = "${jdk}/lib/openjdk";
  ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
  ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
  LC_ALL = "C.UTF-8";
  preConfigure = ''
    # Nix/cache-update sandboxes have no writable real user home. AGP needs
    # Android preferences and metrics state even for unsigned release builds.
    androidBuildUserHome="$TMPDIR/totipo-android-user"
    export ANDROID_USER_HOME="$androidBuildUserHome/.android"
    mkdir -p "$ANDROID_USER_HOME"
    gradleFlagsArray+=("-Duser.home=$androidBuildUserHome")
  '';
  gradleFlags = [
    "--no-configuration-cache"
    "--no-build-cache"
    "--dependency-verification=strict"
    "--warning-mode=all"
    "-Pandroid.aapt2FromMavenOverride=${androidSdk}/libexec/android-sdk/build-tools/36.0.0/aapt2"
  ];
  gradleBuildTask = ":app:assembleRelease";
  doCheck = true;
  gradleCheckTask = "check";
  gradleUpdateTask = ":app:assembleRelease check";
  preBuild = ''
    if [ -z "''${IN_GRADLE_UPDATE_DEPS:-}" ] && [ "${if cacheReady then "yes" else "no"}" != yes ]; then
      echo 'package-deps.json is ungenerated/stale; run nix run path:.#update-package-deps' >&2
      exit 1
    fi
  '';
  installPhase = ''
    runHook preInstall
    mkdir -p "$out/share/totipo-android"
    cp app/build/outputs/apk/release/app-release-unsigned.apk "$out/share/totipo-android/totipo-android-${version}-unsigned.apk"
    runHook postInstall
  '';
  doInstallCheck = true;
  installCheckPhase = ''
    runHook preInstallCheck
    python3 tools/verify-apk.py "$out/share/totipo-android/totipo-android-${version}-unsigned.apk" --unsigned
    runHook postInstallCheck
  '';
  meta = {
    description = "Minimal Totipo Android bootstrap unsigned release APK";
    homepage = "https://github.com/totipo-org/totipo-android";
    license = lib.licenses.asl20;
    platforms = [ "x86_64-linux" ];
    sourceProvenance = with lib.sourceTypes; [ fromSource binaryBytecode ];
  };
})
