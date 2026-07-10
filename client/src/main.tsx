import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import { Ping } from "./App.tsx";
import { ApolloProvider } from "@apollo/client/react";
import { client } from "./apollo.ts";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ApolloProvider client={client}>
      <Ping />
    </ApolloProvider>
  </StrictMode>,
);
