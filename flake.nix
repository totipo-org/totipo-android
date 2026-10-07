{
  description = "Development environment for totipo-android";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";

    llm-agents.url = "github:numtide/llm-agents.nix";

    jailed-agents = {
      url = "github:andersonjoseph/jailed-agents";
      inputs.llm-agents.follows = "llm-agents";
    };
  };

  outputs = { nixpkgs, flake-utils, jailed-agents, ... }:
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
        # The toolchain verifier uses Nix Gradle; app builds use ./gradlew.
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
      in
      {
        formatter = pkgs.nixpkgs-fmt;

        packages = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
          default = androidPackage;
        };
        checks = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
          android = androidPackage;
        };
        apps = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
          update-package-deps = {
            type = "app";
            program = "${androidPackage.mitmCache.updateScript}";
            meta.description = "Update the pinned Android Gradle download cache";
          };
        };

        devShells.ci = pkgs.mkShell (androidEnv // {
          packages = androidPackages;
        });
        devShells.default = pkgs.mkShell (androidEnv // {
          packages = androidPackages ++ [
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
