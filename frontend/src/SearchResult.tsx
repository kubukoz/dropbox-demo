import { FC } from "react";

type SearchResultProps = { imageUrl: string; thumbnailUrl: string };

export const SearchResult: FC<SearchResultProps> = ({
  imageUrl,
  thumbnailUrl,
}) => {
  return (
    <>
      <a href={imageUrl} target="_blank" rel="noreferrer">
        <img src={thumbnailUrl} style={{ height: "200px" }}></img>
      </a>
    </>
  );
};
