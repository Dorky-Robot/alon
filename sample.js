const cssData = {
  ":root": ["--max-width", "768px"],
  ".grid-container": {
    "display": "grid",
    "grid-template-columns": { func: ["repeat", 2, "1fr"] },
    "gap": "10px",
    "@media screen and (max-width: var(--max-width))": {
      ".grid-container": {
        "grid-template-columns": { func: ["repeat", 1, "1fr"] }
      }
    }
  },
  ".grid-item": {
    "border": { func: ["1px", "solid", "#ccc"] },
    "padding": "10px",
    "textAlign": "center",
    "background": ["red", { func: ["linear-gradient", "to right", "red", "orange"] }],
    "transform": [
      { func: ["rotate", "45deg"] },
      { func: ["translateX", "100px"] }
    ],
    "filter": [
      { func: ["blur", "5px"] },
      { func: ["brightness", "0.8"] }
    ],
    "boxShadow": [
      ["10px", "10px", "5px", "0px", { func: ["rgba", 0, 0, 0, 0.75] }],
      ["inset", "0", "0", "10px", { func: ["rgba", 255, 255, 255, 0.5] }]
    ],
    ":hover": {
      "backgroundColor": "gray"
    }
  }
};