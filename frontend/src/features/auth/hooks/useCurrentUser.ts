import { useQuery } from '@tanstack/react-query'

import { ApiError, getCurrentUser } from '@/lib/api'

export const currentUserQueryKey = ['auth', 'me'] as const

export function useCurrentUser() {
  return useQuery({
    queryKey: currentUserQueryKey,
    queryFn: getCurrentUser,
    retry: (failureCount, error) => {
      if (error instanceof ApiError && error.status === 401) {
        return false
      }
      return failureCount < 1
    },
  })
}
