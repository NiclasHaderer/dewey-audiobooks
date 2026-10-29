{
  description = "Development environment for dewey";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }: let
    systems = [ "aarch64-darwin" "x86_64-darwin" "x86_64-linux" "aarch64-linux" ];
    forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
  in {
    formatter = forAllSystems (pkgs: pkgs.nixfmt);

    devShells = forAllSystems (pkgs: {
      default = pkgs.mkShell {
        packages = with pkgs; [
          jdk25              # Gradle runs on it; :taglib needs the FFM API
          clang              # builds the bundled TagLib fork
          cmake
          gnumake
          jextract           # regenerates :taglib's committed FFM bindings
          git
          ffmpeg             # regenerates the audio test fixtures
          atomicparsley      # writes the mp4 freeform tags ffmpeg cannot
          actionlint
          ktlint
          fnm
          go                 # builds the cli, gofmt formats its generated code
          just               # runs the recipes in the justfile
        ];

        shellHook = ''
          # Overrides whatever the login shell exported, so ./gradlew always runs on 25.
          export JAVA_HOME="${pkgs.jdk25.home}"
        '';
      };
    });
  };
}
