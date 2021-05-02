import axios from "axios";
import { useEffect, useState } from "react";
import { SearchBox } from "./SearchBox";
import { SearchResult, Result } from "./SearchResult";

const baseURL = "http://localhost:4000";

type Results = readonly Result[];

const useRunSearch = (props: { onResults(r: Results): void }) => {
  const { onResults } = props;

  const [searching, setSearching] = useState(false);

  const runSearch = (query: string) => {
    const cancelToken = axios.CancelToken.source();
    setSearching(true);
    console.log(`Searching for ${query}`);

    axios
      .get("/search", {
        baseURL,
        params: { query },
        cancelToken: cancelToken.token,
      })
      .then((results) => {
        // todo should decode these results huh...
        // data is any!!!!!!!!!
        onResults(results.data);
      })
      .catch((e) => console.log("Search failed", e))
      .finally(() => setSearching(false));

    // on cancel
    return () => {
      cancelToken.cancel("Search canceled");
      console.info(`Cleaning up search for ${query}`);
    };
  };

  return {
    runSearch,
    searching,
  };
};

const useDeferred = (props: {
  action(): () => void;
  deps: readonly unknown[];
  delayMillis: number;
}) => {
  const { action, deps, delayMillis } = props;

  useEffect(() => {
    // temporary value does nothing
    let nestedCleanup = () => {
      console.log("useDeferred: cleaned up cleanly");
      return;
    };

    const wait = setTimeout(() => {
      const cleanupAction = action();
      nestedCleanup = () => {
        console.log("useDeferred: cleanup nested action");
        cleanupAction();
      };
    }, delayMillis);

    return () => {
      clearTimeout(wait);
      console.info("Cleared timeout");
      // in case it started, we forward the cleanup to the delayed action
      // wow, if only there was a monad that does this...
      nestedCleanup();
    };
  }, deps);
};

export const App = () => {
  const [query, setQuery] = useState("");

  const [results, setResults] = useState<Results>([]);

  const { runSearch, searching } = useRunSearch({
    onResults: (results) => setResults(results),
  });

  useDeferred({
    action() {
      return runSearch(query);
    },
    deps: [query],
    delayMillis: 500,
  });

  const resultViews = results.map((result, i) => {
    return <SearchResult {...result} key={i} />;
  });

  return (
    <>
      <h1 style={{ fontFamily: "Helvetica" }}>Search my images</h1>
      <SearchBox
        placeholder="Search phrase"
        initial={query}
        onChange={(newQuery) => setQuery(newQuery)}
        searching={searching}
      />

      {resultViews}
    </>
  );
};
