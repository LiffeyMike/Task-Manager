import { useQuery } from "@apollo/client/react";
import { graphql } from "./gql";

const PING = graphql(`
  query Ping {
    ping
  }
`);

export function Ping() {
  const { data, loading, error } = useQuery(PING);

  if (loading) return <p>Loading...</p>;
  if (error) return <p>Error</p>;

  return <p>Ping says: {data?.ping}</p>;
}
