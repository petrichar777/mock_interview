export function formatPercent(done: number, total: number): string {
  if (!total) {
    return '0%'
  }
  const percent = Math.round((done / total) * 100)
  return `${percent}%`
}
