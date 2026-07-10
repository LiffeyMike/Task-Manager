import { render, screen } from "@testing-library/react";
import { ApolloProvider } from "@apollo/client/react";
import { client } from "./apollo";
import { Ping } from "./App";

test("renders the ping result", async () => {
  render(
    <ApolloProvider client={client}>
      <Ping />
    </ApolloProvider>,
  );

  expect(await screen.findByText("Ping says: pong")).toBeInTheDocument();
});
