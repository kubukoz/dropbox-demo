import { useEffect, useState } from "react";
import { SearchBox } from "./SearchBox";
import { SearchResult } from "./SearchResult";

export const App = () => {
  const [query, setQuery] = useState("doggos");

  const [searching, setSearching] = useState(false);
  const [results, setResults] = useState<readonly { url: string }[]>([]);

  useEffect(() => {
    setSearching(true);
    const tim = setTimeout(() => {
      setResults(
        query.split("").map((_, i) => ({
          url: "https://placedog.net/" + (200 + i * 10),
        }))
      );
      setSearching(false);
    }, 500);

    () => {
      clearTimeout(tim);
      setSearching(false);
    };
  }, [query]);

  const resultViews = results.map(({ url }, i) => {
    return <SearchResult imageUrl={url} thumbnailUrl={url} key={i} />;
  });

  const ellipsis = searching ? "searching..." : undefined;

  return (
    <>
      <h1 style={{ fontFamily: "Helvetica" }}>Search my doggos</h1>
      <SearchBox
        placeholder="Search phrase"
        initial={query}
        onChange={(newQuery) => setQuery(newQuery)}
      />
      {resultViews}
      {ellipsis}
    </>
  );
};
