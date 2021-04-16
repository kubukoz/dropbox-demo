{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  buildInputs = [ pkgs.tesseract ];
  shellHook = "source ${toString ./env.sh}";
}
