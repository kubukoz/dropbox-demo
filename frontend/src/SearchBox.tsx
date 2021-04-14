import { FC, useState } from "react";

type SearchBoxProps = {
  placeholder: string;
  initial: string;
  onChange: (newValue: string) => void;
  searching: boolean;
};

export const SearchBox: FC<SearchBoxProps> = ({
  placeholder,
  onChange,
  initial,
  searching,
}) => {
  const [value, setValue] = useState(initial);

  const ellipsis = searching ? "searching..." : undefined;

  return (
    <div style={{ paddingBottom: "20px" }}>
      <input
        type="text"
        value={value}
        placeholder={placeholder}
        onChange={(e) => {
          setValue(e.target.value);
          onChange(e.target.value);
        }}
        style={{ display: "inline-block", fontFamily: "Helvetica" }}
      ></input>
      <span style={{ paddingLeft: "10px", fontFamily: "Helvetica" }}>
        {ellipsis}
      </span>
    </div>
  );
};
