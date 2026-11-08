import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function FavoriteButton({ listingId }: { listingId: string }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const key = ['me', 'favorite', listingId]

  const { data: saved = false } = useQuery({
    queryKey: key,
    queryFn: () => api.isFavorite(listingId),
    enabled: !!user,
  })

  const mutation = useMutation({
    mutationFn: (next: boolean) => (next ? api.addFavorite(listingId) : api.removeFavorite(listingId)),
    // Flip immediately; roll back if the request fails
    onMutate: async (next) => {
      await queryClient.cancelQueries({ queryKey: key })
      const previous = queryClient.getQueryData<boolean>(key)
      queryClient.setQueryData(key, next)
      return { previous }
    },
    onError: (_error, _next, context) => queryClient.setQueryData(key, context?.previous ?? false),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['me', 'favorites'] }),
  })

  if (!user) {
    return (
      <button type="button" className="button button--ghost" onClick={() => navigate('/login', { state: { from: location.pathname } })}>
        ♡ Sign in to save
      </button>
    )
  }

  return (
    <button
      type="button"
      className={saved ? 'button' : 'button button--ghost'}
      aria-pressed={saved}
      disabled={mutation.isPending}
      onClick={() => mutation.mutate(!saved)}
    >
      {saved ? '♥ Saved' : '♡ Save'}
    </button>
  )
}
