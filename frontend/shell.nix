let
  pkgs = import ../nix/pkgs.nix {};
in
pkgs.mkShell {
  buildInputs = with pkgs; [ nodejs-14_x ];
}
