let
  pkgs = import ./nix/pkgs.nix {};
in
pkgs.mkShell {
  buildInputs = [ pkgs.tesseract ];
  shellHook = "source ${toString ./env.sh}";
}
