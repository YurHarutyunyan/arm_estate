import { Link } from 'react-router-dom'
import type { SearchItem } from '../api/types'
import { formatPrice } from '../format'
import { ListingImage } from './ListingImage'

/** The server escapes the listing text and only adds <mark> tags, so this HTML is safe to render. */
function Highlighted({ html, fallback }: { html: string | null; fallback: string }) {
  if (!html) return <>{fallback}</>
  return <span dangerouslySetInnerHTML={{ __html: html }} />
}

export function ListingCard({ item }: { item: SearchItem }) {
  return (
    <Link to={`/listings/${item.id}`} className="card listing-card" aria-label={`${item.title}, ${formatPrice(item.priceUsd)} per month`}>
      <ListingImage id={item.id} floors={item.totalFloors} label={`Illustration of ${item.title}`} />
      <div className="listing-card__body">
        <div className="listing-card__price">
          {formatPrice(item.priceUsd)} <small>/ month</small>
        </div>
        <h3 className="listing-card__title">
          <Highlighted html={item.titleHighlight} fallback={item.title} />
        </h3>
        <div className="listing-card__meta">
          {item.rooms} {item.rooms === 1 ? 'room' : 'rooms'} · {item.areaSqm} m² · floor {item.floor}/{item.totalFloors} ·{' '}
          {item.districtName}
        </div>
        {item.descriptionHighlight && (
          <p className="listing-card__snippet">
            <Highlighted html={item.descriptionHighlight} fallback="" />
          </p>
        )}
        <div className="chips">
          {item.furnished && <span className="chip chip--accent">Furnished</span>}
          {item.petsAllowed && <span className="chip chip--accent">Pets allowed</span>}
          {item.amenities.slice(0, 3).map((a) => (
            <span key={a} className="chip">
              {a}
            </span>
          ))}
        </div>
      </div>
    </Link>
  )
}
