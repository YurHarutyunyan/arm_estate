export function formatPrice(usd: number) {
  return `$${usd.toLocaleString('en-US')}`
}
