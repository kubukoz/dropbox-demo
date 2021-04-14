import axios from "axios";
import { useEffect, useState } from "react";
import { SearchBox } from "./SearchBox";
import { SearchResult } from "./SearchResult";

// I'm so sorry
const toResult = (input: { fileName: string }): { url: string } => {
  console.log(input);
  return { url: `http://localhost:4000/view${input.fileName}` };
};

type Result = { fileName: string };
type Results = readonly Result[];

const useRunSearch = ({ onResults }: { onResults: (r: Results) => void }) => {
  const [searching, setSearching] = useState(false);

  let canceled = false;

  const runSearch = (query: string) => {
    setSearching(true);
    axios
      .get(`http://localhost:4000/search`, { params: { query } })
      .then((results) => {
        // todo should decode these results huh...
        if (!canceled) onResults(results.data);
      })
      .finally(() => setSearching(false));
  };

  return {
    runSearch,
    cancelSearch() {
      canceled = true;
    },
    searching,
  };
};

export const App = () => {
  const [query, setQuery] = useState("snap");

  const [results, setResults] = useState<readonly { url: string }[]>([]);

  const { runSearch, cancelSearch, searching } = useRunSearch({
    onResults: (results) => setResults(results.map(toResult)),
  });

  useEffect(() => {
    runSearch(query);

    () => {
      cancelSearch();
    };
  }, [query]);

  const resultViews = results.map(({ url }, i) => {
    return <SearchResult imageUrl={url} thumbnailUrl={url} key={i} />;
  });

  const ellipsis = searching ? "searching..." : undefined;

  return (
    <>
      <h1 style={{ fontFamily: "Helvetica" }}>Search my snapchat</h1>
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
