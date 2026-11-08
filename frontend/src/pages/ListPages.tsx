import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { Listing, SearchItem } from '../api/types'
import { districtLabel } from '../api/types'
import { ListingCard } from '../components/ListingCard'

/** Lists from /api/me come as full listings; cards expect search items. */
function toItem(listing: Listing): SearchItem {
  return {
    ...listing,
    districtName: districtLabel(listing.district),
    createdAt: new Date(listing.createdAt).getTime(),
    titleHighlight: null,
    descriptionHighlight: null,
  }
}

function ListingGrid({ listings, empty }: { listings: Listing[] | undefined; empty: React.ReactNode }) {
  if (!listings) {
    return (
      <div className="results" aria-hidden="true">
        {Array.from({ length: 3 }, (_, i) => (
          <div key={i} className="skeleton" />
        ))}
      </div>
    )
  }
  if (listings.length === 0) return <div className="card empty">{empty}</div>
  return (
    <div className="results">
      {listings.map((l) => (
        <ListingCard key={l.id} item={toItem(l)} />
      ))}
    </div>
  )
}

export function FavoritesPage() {
  const { data, isError, error } = useQuery({ queryKey: ['me', 'favorites'], queryFn: api.favorites })
  return (
    <>
      <div className="page-title">
        <h1>Saved homes</h1>
      </div>
      {isError && <p className="alert">{error.message}</p>}
      <ListingGrid
        listings={data}
        empty={
          <>
            <h3>Nothing saved yet</h3>
            <p className="muted">
              Tap <strong>♡ Save</strong> on a listing to keep it here. <Link to="/">Start searching</Link>
            </p>
          </>
        }
      />
    </>
  )
}

export function MyListingsPage() {
  const { data, isError, error } = useQuery({ queryKey: ['me', 'listings'], queryFn: api.myListings })
  return (
    <>
      <div className="page-title">
        <h1>My listings</h1>
        <Link className="button" to="/listings/new">
          + New listing
        </Link>
      </div>
      {isError && <p className="alert">{error.message}</p>}
      <ListingGrid
        listings={data}
        empty={
          <>
            <h3>You haven’t published anything yet</h3>
            <p className="muted">Create your first listing; it shows up in search within a second.</p>
          </>
        }
      />
    </>
  )
}
