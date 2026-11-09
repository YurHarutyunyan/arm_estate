import { useCallback, useMemo, useState } from 'react'
import { useInfiniteQuery, keepPreviousData } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { api, ApiError, type SearchParams } from '../api/client'
import { Filters, type FilterState } from '../components/Filters'
import { ListingCard } from '../components/ListingCard'
import { SearchBox } from '../components/SearchBox'

const PAGE_SIZE = 18
const MULTI = ['district', 'rooms', 'buildingType'] as const
const SINGLE = ['q', 'minPrice', 'maxPrice', 'furnished', 'petsAllowed', 'sort'] as const

/** All search state lives in the URL, so results can be shared and the Back button works. */
export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const [filtersOpen, setFiltersOpen] = useState(false)

  const state = useMemo(() => {
    const read = (key: string) => params.get(key) ?? ''
    return {
      q: read('q'),
      sort: read('sort'),
      filters: {
        district: params.getAll('district'),
        rooms: params.getAll('rooms'),
        buildingType: params.getAll('buildingType'),
        minPrice: read('minPrice'),
        maxPrice: read('maxPrice'),
        furnished: read('furnished'),
        petsAllowed: read('petsAllowed'),
      } satisfies FilterState,
    }
  }, [params])

  const update = useCallback(
    (changes: Record<string, string | string[]>) => {
      setParams(
        (current) => {
          const next = new URLSearchParams(current)
          for (const [key, value] of Object.entries(changes)) {
            next.delete(key)
            if (Array.isArray(value)) value.forEach((v) => next.append(key, v))
            else if (value) next.set(key, value)
          }
          return next
        },
        { replace: false },
      )
    },
    [setParams],
  )

  const searchParams: SearchParams = {
    q: state.q,
    sort: state.sort,
    size: PAGE_SIZE,
    ...state.filters,
  }
  const invalidPrice =
    state.filters.minPrice !== '' && state.filters.maxPrice !== '' && Number(state.filters.minPrice) > Number(state.filters.maxPrice)

  const query = useInfiniteQuery({
    queryKey: ['search', searchParams],
    queryFn: ({ pageParam, signal }) => api.search({ ...searchParams, cursor: pageParam ?? undefined }, signal),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    placeholderData: keepPreviousData,
    enabled: !invalidPrice,
  })

  const first = query.data?.pages[0]
  const items = query.data?.pages.flatMap((p) => p.items) ?? []
  const activeFilters =
    MULTI.reduce((n, k) => n + state.filters[k].length, 0) +
    SINGLE.filter((k) => k !== 'q' && k !== 'sort' && params.get(k)).length

  return (
    <>
      <section className="hero">
        <h1>
          Find your next home in <span>Yerevan</span>
        </h1>
        <SearchBox
          initialValue={state.q}
          onSearch={(q) => update({ q, sort: '' })}
          onPickDistrict={(district) =>
            update({ district: state.filters.district.includes(district) ? state.filters.district : [...state.filters.district, district] })
          }
        />
      </section>

      <div className="search-layout">
        <Filters
          value={state.filters}
          facets={first?.facets}
          open={filtersOpen}
          onChange={(changes) => update(changes as Record<string, string | string[]>)}
          onClear={() => update({ district: [], rooms: [], buildingType: [], minPrice: '', maxPrice: '', furnished: '', petsAllowed: '' })}
        />

        <section aria-labelledby="results-heading">
          <div className="results-bar">
            <h2 id="results-heading" aria-live="polite">
              {query.isPending && !first ? 'Searching…' : `${(first?.total ?? 0).toLocaleString('en-US')} ${first?.total === 1 ? 'home' : 'homes'}`}
              {state.q && <span className="muted"> for “{state.q}”</span>}
            </h2>
            <div className="actions">
              <button
                type="button"
                className="button button--ghost filters-toggle"
                aria-expanded={filtersOpen}
                aria-controls="filters"
                onClick={() => setFiltersOpen((o) => !o)}
              >
                Filters{activeFilters > 0 ? ` (${activeFilters})` : ''}
              </button>
              <label className="visually-hidden" htmlFor="sort">
                Sort by
              </label>
              <select id="sort" className="select" value={state.sort} onChange={(e) => update({ sort: e.target.value })}>
                <option value="">{state.q ? 'Best match' : 'Newest'}</option>
                {state.q && <option value="NEWEST">Newest</option>}
                <option value="PRICE_ASC">Price: low to high</option>
                <option value="PRICE_DESC">Price: high to low</option>
              </select>
            </div>
          </div>

          {invalidPrice && <p className="alert">Minimum price is higher than maximum price.</p>}
          {query.isError && (
            <p className="alert" role="alert">
              {query.error instanceof ApiError ? query.error.message : 'Search is unavailable right now. Please try again.'}
            </p>
          )}

          {query.isPending && !first ? (
            <div className="results" aria-hidden="true">
              {Array.from({ length: 6 }, (_, i) => (
                <div key={i} className="skeleton" />
              ))}
            </div>
          ) : items.length === 0 ? (
            <div className="card empty">
              <h3>No homes match</h3>
              <p className="muted">Try fewer filters or a different search.</p>
            </div>
          ) : (
            <div className="results">
              {items.map((item) => (
                <ListingCard key={item.id} item={item} />
              ))}
            </div>
          )}

          {query.hasNextPage && (
            <div className="load-more">
              <button
                type="button"
                className="button button--ghost"
                disabled={query.isFetchingNextPage}
                onClick={() => query.fetchNextPage()}
              >
                {query.isFetchingNextPage ? 'Loading…' : 'Show more homes'}
              </button>
            </div>
          )}
        </section>
      </div>
    </>
  )
}
