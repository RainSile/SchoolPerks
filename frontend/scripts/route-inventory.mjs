import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'
export async function routeInventory(root) {
  const file = path.join(root, 'frontend/src/campus-router/index.ts')
  const source = ts.createSourceFile(file, await fs.readFile(file, 'utf8'), ts.ScriptTarget.Latest, true), routes = []
  function inspect(node) {
    if (ts.isVariableDeclaration(node) && node.name.getText(source) === 'routes' && node.initializer && ts.isArrayLiteralExpression(node.initializer)) {
      for (const item of node.initializer.elements) {
        if (!ts.isObjectLiteralExpression(item)) continue
        const property = name => item.properties.find(value => ts.isPropertyAssignment(value) && value.name.getText(source) === name)?.initializer
        const value = property('path'); if (!value || !ts.isStringLiteral(value)) continue
        const meta = property('meta')
        const permission = meta && ts.isObjectLiteralExpression(meta) ? meta.properties.find(value => ts.isPropertyAssignment(value) && value.name.getText(source) === 'permission')?.initializer?.getText(source).replace(/^['"]|['"]$/g, '') : null
        let component = ''
        const findImport = child => { if (ts.isCallExpression(child) && child.expression.kind === ts.SyntaxKind.ImportKeyword && child.arguments[0] && ts.isStringLiteral(child.arguments[0])) component = child.arguments[0].text; ts.forEachChild(child, findImport) }
        if (property('component')) findImport(property('component'))
        const redirect = property('redirect')
        routes.push({ path: value.text, component, permission, redirect: redirect && ts.isStringLiteral(redirect) ? redirect.text : null, excluded: value.text === '/merchant/marketing' })
      }
    }
    ts.forEachChild(node, inspect)
  }
  inspect(source); return routes
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (!process.argv[2]) throw new Error('Supply the route inventory output path')
  const routes = await routeInventory(fileURLToPath(new URL('../../', import.meta.url)))
  await fs.writeFile(path.resolve(process.argv[2]), JSON.stringify(routes, null, 2))
  console.log(`Exported ${routes.length} current routes from TypeScript AST.`)
}
