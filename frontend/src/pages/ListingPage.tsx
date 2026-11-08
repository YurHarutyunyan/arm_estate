import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { districtLabel } from '../api/types'
import { useAuth } from '../auth/context'
import { FavoriteButton } from '../components/FavoriteButton'
import { formatPrice } from '../format'
import { ListingImage } from '../components/ListingImage'
import { NotFoundPage } from './NotFoundPage'

const BUILDING = { STONE: 'Stone', PANEL: 'Panel', MONOLITH: 'Monolith' } as const

export function ListingPage() {
  const { id = '' } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const query = useQuery({
    queryKey: ['listing', id],
    queryFn: ({ signal }) => api.listing(id, signal),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 2,
  })

  const remove = useMutation({
    mutationFn: () => api.deleteListing(id, query.data!.etag),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['listing', id] })
      queryClient.invalidateQueries({ queryKey: ['search'] })
      queryClient.invalidateQueries({ queryKey: ['me', 'listings'] })
      navigate('/my-listings')
    },
    onError: (error) => {
      setDeleteError(
        error instanceof ApiError && error.status === 412
          ? 'Someone changed this listing a moment ago. The page has been reloaded; check it and try again.'
          : error.message,
      )
      if (error instanceof ApiError && error.status === 412) query.refetch()
    },
  })

  if (query.error instanceof ApiError && query.error.status === 404) return <NotFoundPage />
  if (query.isError) return <p className="alert" role="alert">{query.error.message}</p>
  if (!query.data) return <div className="skeleton" aria-label="Loading listing" />

  const { listing } = query.data
  const canManage = !!user && (user.id === listing.ownerId || user.roles.includes('ADMIN'))

  return (
    <article>
      <p>
        <Link to="/">← Back to search</Link>
      </p>
      <div className="detail">
        <div className="card detail__media">
          <ListingImage id={listing.id} floors={listing.totalFloors} label={`Illustration of ${listing.title}`} />
        </div>
        <div className="card detail__panel">
          <div className="detail__price">
            {formatPrice(listing.priceUsd)} <span className="muted" style={{ fontSize: '1rem' }}>/ month</span>
          </div>
          <h1 style={{ fontSize: '1.4rem' }}>{listing.title}</h1>
          <p className="muted" style={{ margin: 0 }}>
            {listing.address}, {districtLabel(listing.district)}
          </p>
          <dl className="facts">
            <div>
              <dt>Rooms</dt>
              <dd>{listing.rooms}</dd>
            </div>
            <div>
              <dt>Area</dt>
              <dd>{listing.areaSqm} m²</dd>
            </div>
            <div>
              <dt>Floor</dt>
              <dd>
                {listing.floor} of {listing.totalFloors}
              </dd>
            </div>
            <div>
              <dt>Building</dt>
              <dd>{BUILDING[listing.buildingType]}</dd>
            </div>
          </dl>
          <div className="chips">
            {listing.furnished && <span className="chip chip--accent">Furnished</span>}
            {listing.petsAllowed && <span className="chip chip--accent">Pets allowed</span>}
            {listing.amenities.map((a) => (
              <span key={a} className="chip">
                {a}
              </span>
            ))}
          </div>
          <div className="actions">
            <FavoriteButton listingId={listing.id} />
            {canManage && (
              <>
                <Link className="button button--ghost" to={`/listings/${listing.id}/edit`}>
                  Edit
                </Link>
                <button
                  type="button"
                  className="button button--danger"
                  disabled={remove.isPending}
                  onClick={() => {
                    setDeleteError(null)
                    if (window.confirm('Delete this listing? This cannot be undone.')) remove.mutate()
                  }}
                >
                  Delete
                </button>
              </>
            )}
          </div>
          {deleteError && (
            <p className="alert" role="alert">
              {deleteError}
            </p>
          )}
        </div>
      </div>
      <section className="card detail__description" aria-labelledby="description-heading">
        <h2 id="description-heading" style={{ fontSize: '1.1rem', marginBottom: 8 }}>
          About this home
        </h2>
        {listing.description}
      </section>
    </article>
  )
}
