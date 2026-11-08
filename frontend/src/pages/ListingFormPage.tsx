import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { DISTRICTS, type ListingInput } from '../api/types'
import { Field } from './AuthPages'

const EMPTY: ListingInput = {
  title: '',
  description: '',
  district: 'KENTRON',
  address: '',
  priceUsd: 600,
  rooms: 2,
  areaSqm: 60,
  floor: 3,
  totalFloors: 9,
  buildingType: 'STONE',
  amenities: [],
  furnished: true,
  petsAllowed: false,
  imageUrl: null,
}

const AMENITIES = ['balcony', 'parking', 'elevator', 'air conditioning', 'dishwasher', 'washing machine', 'heating', 'wifi', 'terrace', 'security']

/** Create a listing, or edit one. Edits send the ETag back, so a concurrent change is detected. */
export function ListingFormPage() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<ListingInput>(EMPTY)
  const [etag, setEtag] = useState('')
  const [conflict, setConflict] = useState(false)

  const existing = useQuery({
    queryKey: ['listing', id],
    queryFn: ({ signal }) => api.listing(id!, signal),
    enabled: editing,
  })

  useEffect(() => {
    if (!existing.data) return
    const { listing } = existing.data
    setForm({
      title: listing.title,
      description: listing.description,
      district: listing.district,
      address: listing.address,
      priceUsd: listing.priceUsd,
      rooms: listing.rooms,
      areaSqm: listing.areaSqm,
      floor: listing.floor,
      totalFloors: listing.totalFloors,
      buildingType: listing.buildingType,
      amenities: listing.amenities,
      furnished: listing.furnished,
      petsAllowed: listing.petsAllowed,
      imageUrl: listing.imageUrl,
    })
    setEtag(existing.data.etag)
  }, [existing.data])

  const save = useMutation({
    mutationFn: () => (editing ? api.updateListing(id!, etag, form) : api.createListing(form)),
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['listing', saved.id] })
      queryClient.invalidateQueries({ queryKey: ['me', 'listings'] })
      queryClient.invalidateQueries({ queryKey: ['search'] })
      navigate(`/listings/${saved.id}`)
    },
    onError: (error) => setConflict(error instanceof ApiError && error.status === 412),
  })

  const errors = save.error instanceof ApiError ? save.error.fieldErrors : {}
  const floorError = form.floor > form.totalFloors ? 'Floor can’t be higher than the building' : undefined

  const text = (key: 'title' | 'description' | 'address') => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm({ ...form, [key]: e.target.value })
  const number = (key: 'priceUsd' | 'rooms' | 'areaSqm' | 'floor' | 'totalFloors') => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [key]: e.target.value === '' ? 0 : Number(e.target.value) })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!floorError) save.mutate()
  }

  if (editing && existing.isError) return <p className="alert">{existing.error.message}</p>

  return (
    <form className="card form-page form-page--wide" onSubmit={submit} noValidate>
      <h1>{editing ? 'Edit listing' : 'New listing'}</h1>

      {conflict && (
        <div className="alert" role="alert">
          <p style={{ margin: '0 0 8px' }}>Someone else saved this listing while you were editing. Your changes were not saved.</p>
          <button
            type="button"
            className="button button--ghost"
            onClick={async () => {
              setConflict(false)
              save.reset()
              await existing.refetch()
            }}
          >
            Load the latest version
          </button>
        </div>
      )}
      {save.isError && !conflict && Object.keys(errors).length === 0 && (
        <p className="alert" role="alert">
          {save.error.message}
        </p>
      )}

      <div className="form-grid">
        <div className="field--full">
          <Field id="title" label="Title" error={errors.title}>
            <input id="title" className="input" maxLength={120} required value={form.title} onChange={text('title')} />
          </Field>
        </div>
        <Field id="district" label="District" error={errors.district}>
          <select
            id="district"
            className="select"
            value={form.district}
            onChange={(e) => setForm({ ...form, district: e.target.value as ListingInput['district'] })}
          >
            {DISTRICTS.map((d) => (
              <option key={d.value} value={d.value}>
                {d.label}
              </option>
            ))}
          </select>
        </Field>
        <Field id="address" label="Address" error={errors.address}>
          <input id="address" className="input" maxLength={200} required value={form.address} onChange={text('address')} />
        </Field>
        <Field id="priceUsd" label="Monthly price (USD)" error={errors.priceUsd}>
          <input id="priceUsd" className="input" type="number" min={50} max={20000} value={form.priceUsd || ''} onChange={number('priceUsd')} />
        </Field>
        <Field id="rooms" label="Rooms" error={errors.rooms}>
          <input id="rooms" className="input" type="number" min={1} max={10} value={form.rooms || ''} onChange={number('rooms')} />
        </Field>
        <Field id="areaSqm" label="Area (m²)" error={errors.areaSqm}>
          <input id="areaSqm" className="input" type="number" min={10} max={1000} value={form.areaSqm || ''} onChange={number('areaSqm')} />
        </Field>
        <Field id="buildingType" label="Building" error={errors.buildingType}>
          <select
            id="buildingType"
            className="select"
            value={form.buildingType}
            onChange={(e) => setForm({ ...form, buildingType: e.target.value as ListingInput['buildingType'] })}
          >
            <option value="STONE">Stone</option>
            <option value="PANEL">Panel</option>
            <option value="MONOLITH">Monolith</option>
          </select>
        </Field>
        <Field id="floor" label="Floor" error={errors.floor ?? floorError}>
          <input id="floor" className="input" type="number" min={0} max={40} value={form.floor} onChange={number('floor')} />
        </Field>
        <Field id="totalFloors" label="Floors in building" error={errors.totalFloors}>
          <input id="totalFloors" className="input" type="number" min={1} max={40} value={form.totalFloors || ''} onChange={number('totalFloors')} />
        </Field>
        <div className="field--full">
          <Field id="description" label="Description" error={errors.description}>
            <textarea id="description" className="textarea" rows={5} maxLength={4000} value={form.description} onChange={text('description')} />
          </Field>
        </div>
        <fieldset className="field--full" style={{ border: 0, padding: 0, margin: 0 }}>
          <legend className="field__label" style={{ marginBottom: 6 }}>
            Amenities
          </legend>
          <div className="chips">
            {AMENITIES.map((a) => (
              <label key={a} className="check chip">
                <input
                  type="checkbox"
                  checked={form.amenities.includes(a)}
                  onChange={(e) =>
                    setForm({ ...form, amenities: e.target.checked ? [...form.amenities, a] : form.amenities.filter((x) => x !== a) })
                  }
                />
                {a}
              </label>
            ))}
          </div>
        </fieldset>
        <label className="check">
          <input type="checkbox" checked={form.furnished} onChange={(e) => setForm({ ...form, furnished: e.target.checked })} />
          Furnished
        </label>
        <label className="check">
          <input type="checkbox" checked={form.petsAllowed} onChange={(e) => setForm({ ...form, petsAllowed: e.target.checked })} />
          Pets allowed
        </label>
      </div>

      <div className="actions">
        <button className="button" type="submit" disabled={save.isPending || (editing && !etag)}>
          {save.isPending ? 'Saving…' : editing ? 'Save changes' : 'Publish listing'}
        </button>
        <button type="button" className="button button--ghost" onClick={() => navigate(-1)}>
          Cancel
        </button>
      </div>
    </form>
  )
}
