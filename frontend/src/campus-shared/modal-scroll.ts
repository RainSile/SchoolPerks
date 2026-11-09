let locks = 0, previousOverflow = ''
// Nested avatar selection/cropping may unmount together on a route or account change.
export function lockDocumentScroll() {
  if (locks++ === 0) { previousOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden' }
  let released = false
  return () => {
    if (released) return
    released = true
    if (--locks === 0) document.body.style.overflow = previousOverflow
  }
}
