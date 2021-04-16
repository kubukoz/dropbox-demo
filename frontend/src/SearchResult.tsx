import { FC } from "react";

export type Result = {
  imageUrl: string;
  thumbnailUrl: string;
  content: string;
};

export const SearchResult: FC<Result> = ({
  imageUrl,
  thumbnailUrl,
  content,
}) => {
  return (
    <>
      <a href={imageUrl} target="_blank" rel="noreferrer" title={content}>
        <img src={thumbnailUrl} style={{ height: "200px" }}></img>
      </a>
    </>
  );
};
