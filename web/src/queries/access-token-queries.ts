import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Api, CreateAccessToken, UpdateAccessToken, UUID } from "@dewey/client"
import { queries } from "./query-definitions"

export const useAccessTokens = () => useQuery(queries.accessTokens)

export const useCreateAccessToken = () => {
  const queryClient = useQueryClient()
  return useMutation({
    meta: { action: "create the access token" },
    mutationFn: (token: CreateAccessToken) => Api.createAccessToken(token),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queries.accessTokens.queryKey }),
  })
}

export const useUpdateAccessToken = () => {
  const queryClient = useQueryClient()
  return useMutation({
    meta: { action: "update the access token" },
    mutationFn: ({ tokenId, update }: { tokenId: UUID; update: UpdateAccessToken }) =>
      Api.updateAccessToken({ tokenId }, update),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queries.accessTokens.queryKey }),
  })
}

export const useRevealAccessToken = () =>
  useMutation({
    meta: { action: "show the access token" },
    mutationFn: ({ tokenId, password }: { tokenId: UUID; password: string }) =>
      Api.revealAccessToken({ tokenId }, { password }),
  })

export const useRevokeAccessToken = () => {
  const queryClient = useQueryClient()
  return useMutation({
    meta: { action: "revoke the access token" },
    mutationFn: ({ tokenId, password }: { tokenId: UUID; password: string }) =>
      Api.revokeAccessToken({ tokenId }, { password }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queries.accessTokens.queryKey }),
  })
}
