import { SimpleGrid, Stack, TextInput } from "@mantine/core";
import { IconSearch } from "@tabler/icons-react";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "../../../shared/components/PageState";
import { SearchableProfile } from "../components/SearchableProfile";
import { searchProfiles } from "../../user/api/userApi";

export function SearchPage() {
  const [value, setValue] = useState("");
  const [query, setQuery] = useState("");
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(value.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [value]);
  const results = useQuery({
    queryKey: ["profile-search", query],
    enabled: query.length >= 2,
    queryFn: () => searchProfiles(query),
  });
  return (
    <div>
      <header className="page-heading">
        <div>
          <h1>Find players</h1>
          <p>Search by username or display name.</p>
        </div>
      </header>
      <Stack gap="lg">
        <TextInput
          leftSection={<IconSearch size={18} />}
          size="md"
          placeholder="Try a username or name"
          value={value}
          onChange={(event) => setValue(event.currentTarget.value)}
          aria-label="Search players"
        />
        {value.trim().length < 2 ? (
          <EmptyState
            title="Start with two characters"
            description="Search only shows players who are discoverable."
          />
        ) : results.isLoading ? (
          <LoadingState label="Searching players" />
        ) : results.isError ? (
          <ErrorState
            message={results.error.message}
            onRetry={() => void results.refetch()}
          />
        ) : results.data?.items.length ? (
          <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
            {results.data.items.map((profile) => (
              <SearchableProfile key={profile.publicId} profile={profile} />
            ))}
          </SimpleGrid>
        ) : (
          <EmptyState
            title="No players found"
            description="Try a different name or username."
          />
        )}
      </Stack>
    </div>
  );
}
