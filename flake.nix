{
  description = "Development environment for totipo-android";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";

    java-dev-tools = {
      url = "github:ingon/java-dev-tools";
      inputs.nixpkgs.follows = "nixpkgs";
    };

    llm-agents.url = "github:numtide/llm-agents.nix";

    jailed-agents = {
      url = "github:andersonjoseph/jailed-agents";
      inputs.llm-agents.follows = "llm-agents";
    };
  };

  outputs = { nixpkgs, flake-utils, java-dev-tools, jailed-agents, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            # Allow only the Android SDK components selected below.
            allowUnfreePredicate = pkg: builtins.elem (nixpkgs.lib.getName pkg) [
              "androidsdk"
              "android-sdk-cmdline-tools"
              "android-sdk-platforms"
              "android-sdk-build-tools"
              "android-sdk-platform-tools"
              "cmdline-tools"
              "platforms"
              "build-tools"
              "platform-tools"
            ];
          };
        };
        jdk = pkgs.jdk17_headless;
        gradle = pkgs.gradle_9.override { java = jdk; };
        javaEditor = java-dev-tools.lib.mkAndroidEditor {
          inherit pkgs jdk gradle;
          aapt2 = "${sdkRoot}/build-tools/36.0.0/aapt2";
        };
        android = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "37.0" ];
          buildToolsVersions = [ "36.0.0" ];
          platformToolsVersion = "37.0.1";
          cmdLineToolsVersion = "22.0";
          toolsVersion = null;
          includeSources = false;
          includeEmulator = false;
          includeSystemImages = false;
          includeNDK = false;
          includeCmake = false;
          includeExtras = [ ];
        };
        androidSdk = android.androidsdk;
        sdkRoot = "${androidSdk}/libexec/android-sdk";
        # Package, development, CI and jailed agents share these tool derivations.
        androidPackages = [ jdk gradle pkgs.python3 androidSdk ];
        androidEnv = {
          JAVA_HOME = "${jdk}/lib/openjdk";
          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/36.0.0/aapt2";
        };
        androidPackage = pkgs.callPackage ./package.nix {
          inherit jdk gradle androidSdk;
        };
        # Release fixtures need the caller's source/public identity, outside the
        # Android package filter. Run this suite once, without package-only skips.
        releaseTestSource = pkgs.lib.fileset.toSource {
          root = ./.;
          fileset = pkgs.lib.fileset.unions [
            ./tools/release
            ./tools/verify-apk.py
            ./build.gradle.kts
            ./app/build.gradle.kts
            ./release/signing
            ./.github/workflows/release.yml
          ];
        };
        releaseCommands = [ "prepare-release" "release-tag" ];
        releasePrograms = builtins.listToAttrs (map
          (name: {
            inherit name;
            value = pkgs.writeShellScriptBin name ''
              export PATH=${pkgs.lib.makeBinPath [ pkgs.python3 pkgs.git pkgs.openssh jdk pkgs.coreutils ]}
              export JAVA_HOME=${jdk}/lib/openjdk
              export GIT_SSL_CAINFO=${pkgs.cacert}/etc/ssl/certs/ca-bundle.crt
              export SSL_CERT_FILE=${pkgs.cacert}/etc/ssl/certs/ca-bundle.crt
              exec python3 -B ${./tools/release}/release.py ${name} "$@"
            '';
          })
          releaseCommands);
      in
      {
        formatter = pkgs.nixpkgs-fmt;

        packages = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
          default = androidPackage;
        };
        checks = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
          android = androidPackage;
          release-tooling = pkgs.runCommand "release-tooling"
            {
              nativeBuildInputs = [ pkgs.python3 pkgs.git jdk ];
              JAVA_HOME = "${jdk}/lib/openjdk";
            } ''
            export HOME="$TMPDIR/home"
            mkdir -p "$HOME"
            cp -R ${releaseTestSource} source
            chmod -R u+w source
            cd source
            python3 -B -m unittest discover -s tools/release -p 'test_*.py'
            touch "$out"
          '';
          release-apps = pkgs.runCommand "release-app-wrappers"
            {
              nativeBuildInputs = [ pkgs.git pkgs.gnugrep ];
            } ''
            export HOME="$TMPDIR/home"
            mkdir -p "$HOME" "$TMPDIR/caller"
            cd "$TMPDIR/caller"
            git init -b main
            git -c user.name=Fixture -c user.email=fixture@example.invalid commit --allow-empty -m Fixture
            ${pkgs.lib.concatMapStringsSep "\n" (name: ''
              ${releasePrograms.${name}}/bin/${name} --help > /dev/null
              if ${releasePrograms.${name}}/bin/${name} 0.1.2 > response 2>&1; then
                echo "Unexpected success in incomplete caller fixture" >&2
                exit 1
              fi
              grep -q 'Missing/nonregular VERSION' response
              test ! -e VERSION && test ! -e VERSION_CODE
            '') releaseCommands}
            touch "$out"
          '';
        };
        apps = pkgs.lib.optionalAttrs (system == "x86_64-linux") ((builtins.mapAttrs
          (name: program: {
            type = "app";
            program = "${program}/bin/${name}";
            meta.description = "Totipo Android ${name} in the caller's Git worktree";
          })
          releasePrograms) // {
          update-package-deps = {
            type = "app";
            program = "${androidPackage.mitmCache.updateScript}";
            meta.description = "Update the pinned Android Gradle download cache";
          };
        });

        devShells.ci = pkgs.mkShell (androidEnv // {
          packages = androidPackages;
        });
        devShells.default = pkgs.mkShell (javaEditor.shellEnv // androidEnv // {
          packages = androidPackages ++ javaEditor.packages ++ [
            pkgs.helix
            (jailed-agents.lib.${system}.makeJailedCodex {
              fwdEnv = [ "JAVA_HOME" "ANDROID_HOME" "ANDROID_SDK_ROOT" "GRADLE_OPTS" ];

              extraPkgs = androidPackages;
            })
          ];
        });
      });

  nixConfig = {
    extra-substituters = [ "https://cache.numtide.com" ];
    extra-trusted-public-keys = [
      "niks3.numtide.com-1:DTx8wZduET09hRmMtKdQDxNNthLQETkc/yaX7M4qK0g="
    ];
  };
}
