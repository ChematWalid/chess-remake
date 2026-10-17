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
import { getFriends, getOutgoingRequests } from "../../social/api/socialApi";
import { DraftStatus } from "../../../shared/drafts/DraftStatus";
import { useSyncedDraft } from "../../../shared/drafts/useSyncedDraft";

export function SearchPage() {
  const searchDraft = useSyncedDraft("player-search", { value: "" });
  const value = searchDraft.value.value;
  const [query, setQuery] = useState("");
  const friends = useQuery({ queryKey: ["friends"], queryFn: getFriends });
  const outgoing = useQuery({
    queryKey: ["friend-requests", "outgoing"],
    queryFn: getOutgoingRequests,
  });
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
        <DraftStatus
          status={searchDraft.status}
          hasDraft={searchDraft.hasDraft}
          onClear={searchDraft.clear}
        />
        <TextInput
          leftSection={<IconSearch size={18} />}
          size="md"
          placeholder="Try a username or name"
          value={value}
          onChange={(event) =>
            searchDraft.update({ value: event.currentTarget.value })
          }
          aria-label="Search players"
        />
        {value.trim().length < 2 ? (
          <EmptyState
            title="Start with two characters"
            description="Search only shows players who are discoverable."
          />
        ) : results.isLoading ? (
          <LoadingState label="Searching players" />
        ) : results.isError && !results.data ? (
          <ErrorState
            message={results.error.message}
            onRetry={() => void results.refetch()}
          />
        ) : results.data?.items.length ? (
          <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
            {results.data.items.map((profile) => (
              <SearchableProfile
                key={profile.publicId}
                profile={profile}
                isFriend={
                  friends.data?.some(
                    (friend) => friend.publicUserId === profile.publicId,
                  ) ?? false
                }
                isRequested={
                  outgoing.data?.some(
                    (request) => request.username === profile.username,
                  ) ?? false
                }
                relationshipLoading={
                  friends.isLoading ||
                  outgoing.isLoading ||
                  (friends.isError && !friends.data) ||
                  (outgoing.isError && !outgoing.data)
                }
              />
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
