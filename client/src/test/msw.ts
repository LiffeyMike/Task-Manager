import { setupServer } from "msw/node";
import { graphql, HttpResponse } from "msw";

export const server = setupServer(graphql.query("Ping", () => HttpResponse.json({ data: { ping: "pong" } })));
