export interface MapInstance { add(markers: unknown[]): void; remove(markers: unknown[]): void; setFitView(): void; setCenter(center: unknown): void; destroy(): void }
interface Converted { getLng(): number; getLat(): number }
interface AmapApi { Map: new (element: HTMLElement, options: object) => MapInstance; Marker: new (options: object) => unknown; convertFrom(points: number[][], type: 'gps', callback: (status: string, result: { locations?: Converted[] }) => void): void }
declare global { interface Window { AMap?: AmapApi; _AMapSecurityConfig?: { serviceHost: string } } }
let pending: Promise<AmapApi> | null = null
export function loadAmap(cap: { jsKey: string; proxyPath: string }): Promise<AmapApi> {
  if (window.AMap) return Promise.resolve(window.AMap)
  if (pending) return pending
  window._AMapSecurityConfig = { serviceHost: location.origin + cap.proxyPath }
  pending = new Promise<AmapApi>((resolve, reject) => {
    const script = document.createElement('script')
    const fail = (message: string) => { clearTimeout(timer); script.remove(); reject(new Error(message)) }
    const timer = setTimeout(() => fail('地图加载超时，请检查网络后重试'), 20000)
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(cap.jsKey)}`
    script.onload = () => { clearTimeout(timer); if (window.AMap) resolve(window.AMap); else fail('地图授权或加载失败') }
    script.onerror = () => fail('地图加载失败，请检查网络后重试')
    document.head.appendChild(script)
  }).catch(error => { pending = null; throw error })
  return pending
}
