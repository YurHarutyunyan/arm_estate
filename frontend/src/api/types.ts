export type District =
  | 'KENTRON' | 'ARABKIR' | 'DAVTASHEN' | 'AJAPNYAK' | 'MALATIA_SEBASTIA' | 'SHENGAVIT'
  | 'EREBUNI' | 'NOR_NORK' | 'AVAN' | 'KANAKER_ZEYTUN' | 'NORK_MARASH' | 'NUBARASHEN'

export type BuildingType = 'STONE' | 'PANEL' | 'MONOLITH'

export type SearchSort = 'RELEVANCE' | 'PRICE_ASC' | 'PRICE_DESC' | 'NEWEST'

export type Role = 'USER' | 'OWNER' | 'ADMIN'

export const DISTRICTS: { value: District; label: string }[] = [
  { value: 'KENTRON', label: 'Kentron' },
  { value: 'ARABKIR', label: 'Arabkir' },
  { value: 'DAVTASHEN', label: 'Davtashen' },
  { value: 'AJAPNYAK', label: 'Ajapnyak' },
  { value: 'MALATIA_SEBASTIA', label: 'Malatia-Sebastia' },
  { value: 'SHENGAVIT', label: 'Shengavit' },
  { value: 'EREBUNI', label: 'Erebuni' },
  { value: 'NOR_NORK', label: 'Nor Nork' },
  { value: 'AVAN', label: 'Avan' },
  { value: 'KANAKER_ZEYTUN', label: 'Kanaker-Zeytun' },
  { value: 'NORK_MARASH', label: 'Nork-Marash' },
  { value: 'NUBARASHEN', label: 'Nubarashen' },
]

export const districtLabel = (value: string) => DISTRICTS.find((d) => d.value === value)?.label ?? value

export interface SearchItem {
  id: string
  title: string
  district: District
  districtName: string
  address: string
  priceUsd: number
  rooms: number
  areaSqm: number
  floor: number
  totalFloors: number
  buildingType: BuildingType
  amenities: string[]
  furnished: boolean
  petsAllowed: boolean
  imageUrl: string | null
  createdAt: number
  /** HTML-escaped by the server; only <mark> tags are added. */
  titleHighlight: string | null
  descriptionHighlight: string | null
}

export interface Bucket {
  value: string
  label: string
  count: number
}

export interface SearchResult {
  total: number
  items: SearchItem[]
  facets: {
    districts: Bucket[]
    rooms: Bucket[]
    buildingTypes: Bucket[]
    price: { min: number | null; max: number | null }
  }
  nextCursor: string | null
}

export interface Suggestions {
  districts: { value: District; label: string }[]
  listings: { id: string; title: string; districtName: string }[]
}

export interface Listing {
  id: string
  ownerId: string
  title: string
  description: string
  district: District
  address: string
  priceUsd: number
  rooms: number
  areaSqm: number
  floor: number
  totalFloors: number
  buildingType: BuildingType
  amenities: string[]
  furnished: boolean
  petsAllowed: boolean
  imageUrl: string | null
  createdAt: string
  updatedAt: string
}

export type ListingInput = Omit<Listing, 'id' | 'ownerId' | 'createdAt' | 'updatedAt'>

export interface Profile {
  id: string
  email: string
  displayName: string
  roles: Role[]
}

export interface AuthResponse {
  token: string
  expiresAt: string
  user: Profile
}
