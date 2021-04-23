import java.nio.file.StandardOpenOption
import $ivy.`io.circe::circe-parser:0.14.0-M5`
import $ivy.`io.circe::circe-generic:0.14.0-M5`
import ammonite.ops._
import io.circe.generic.auto._

val raw = read(pwd / "sculpt.json")
val parsed = io.circe.parser.parse(raw).toOption.get

case class Dep(sym: List[String], uses: List[String])

val decoded = parsed.as[List[Dep]].toOption.get

case class DepMerged(what: String, onWhat: String)

def convert(d: Dep): DepMerged = DepMerged(d.sym.mkString("."), d.uses.mkString("."))
def toDot(d: DepMerged): String = s""""${d.what}" -> "${d.onWhat}";"""

// Obviously, I only realised allowlisting this would've been much more efficient to write
// and probably more accurate too
// but this helped figure out which parts I actually want to show (spoiler: everything with F[_])
val exclusions = Set(
  "pkt:com.pkt:kubukoz.pkt:util.o:DiscriminatorCodecs",
  "pkt:com.pkt:kubukoz.pkt:util.tr:DiscriminatorCodecs",
  "pkt:com.pkt:kubukoz.pkt:util.o:FileUtils",
  "pkt:com.pkt:kubukoz.pkt:shared.cl:FileMetadata",
  "pkt:com.pkt:kubukoz.pkt:shared.cl:FileData",
  "pkt:com.pkt:kubukoz.pkt:shared.o:FileMetadata",
  "pkt:com.pkt:kubukoz.pkt:shared.o:FileData",
  "pkt:com.pkt:kubukoz.pkt:shared.o:Path",
  "pkt:com.pkt:kubukoz.pkt:shared.cl:Path",
  "pkt:com.pkt:kubukoz.pkt:dropbox.cl:ErrorResponse",
  "pkt:com.pkt:kubukoz.pkt:dropbox.cl:FileDownload",
  "pkt:com.pkt:kubukoz.pkt:dropbox.o:ErrorResponse",
  "pkt:com.pkt:kubukoz.pkt:dropbox.o:FileDownload",
  "pkt:com.pkt:kubukoz.pkt:dropbox.tr:Path",
  "pkt:com.pkt:kubukoz.pkt:dropbox.o:Path",
  "pkt:com.pkt:kubukoz.pkt:dropbox.tr:Metadata",
  "pkt:com.pkt:kubukoz.pkt:dropbox.o:Metadata",
  "pkt:com.pkt:kubukoz.pkt:dropbox.cl:Paginable",
  "pkt:com.pkt:kubukoz.pkt:dropbox.o:Paginable",
  "pkt:com.pkt:kubukoz.o:IndexRequest",
  "pkt:com.pkt:kubukoz.cl:IndexRequest",
  "pkt:com.pkt:kubukoz.o:SearchResult",
  "pkt:com.pkt:kubukoz.cl:SearchResult",
  "pkt:com.pkt:kubukoz.pkt:indexer.o:FileDocument",
  "pkt:com.pkt:kubukoz.pkt:indexer.cl:FileDocument",
  "pkt:scala",
  "pkt:cats",
  "pkt:java",
  "pkt:fs2",
  "pkt:org",
  "pkt:com.pkt:comcast",
  "pkt:shapeless",
  "pkt:io",
  "pkt:ciris",
)

def excluded(d: DepMerged) = Set(d.what, d.onWhat).exists(tpe => exclusions.exists(tpe.startsWith(_)))

def simplify(s: String) = s.split(":").last

def referencesSelf(d: DepMerged) = d.what == d.onWhat

val dot = decoded
  .map(convert)
  .filterNot(excluded)
  .map(d => d.copy(what = simplify(d.what), onWhat = simplify(d.onWhat)))
  .filterNot(referencesSelf)
  .distinct
  .map(toDot)
  .map("  " + _)
  .mkString("digraph G {\n", "\n", "\n}\n")

println(dot)
