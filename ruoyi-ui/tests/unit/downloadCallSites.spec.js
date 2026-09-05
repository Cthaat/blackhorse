import { readdirSync, readFileSync } from 'node:fs'
import { relative, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const DOWNLOAD_CALL_PATTERN = /\bproxy\.(?:download|\$download\.(?:name|resource|zip))\s*\(/g

function listSourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) {
      return listSourceFiles(path)
    }
    return /\.(?:js|vue)$/.test(entry.name) ? [path] : []
  })
}

function findClosingParenthesis(source, openingIndex) {
  let depth = 0
  let quote
  let escaped = false

  for (let index = openingIndex; index < source.length; index++) {
    const character = source[index]
    if (quote) {
      if (escaped) {
        escaped = false
      } else if (character === '\\') {
        escaped = true
      } else if (character === quote) {
        quote = undefined
      }
      continue
    }
    if (character === "'" || character === '"' || character === '`') {
      quote = character
    } else if (character === '(') {
      depth++
    } else if (character === ')' && --depth === 0) {
      return index
    }
  }
  return -1
}

describe('download UI call sites', () => {
  it('explicitly consumes every rejection-capable download promise', () => {
    const sourceRoot = resolve(process.cwd(), 'src')
    const callSites = []
    const violations = []

    for (const file of listSourceFiles(sourceRoot)) {
      const source = readFileSync(file, 'utf8')
      for (const match of source.matchAll(DOWNLOAD_CALL_PATTERN)) {
        const line = source.slice(0, match.index).split('\n').length
        const displayPath = relative(sourceRoot, file).replaceAll('\\', '/')
        const location = `${displayPath}:${line}`
        callSites.push(location)

        const lineStart = source.lastIndexOf('\n', match.index) + 1
        const prefix = source.slice(lineStart, match.index)
        const openingIndex = match.index + match[0].lastIndexOf('(')
        const closingIndex = findClosingParenthesis(source, openingIndex)
        const suffix = closingIndex < 0 ? '' : source.slice(closingIndex + 1, closingIndex + 80)
        if (!/^\s*void\s*$/.test(prefix) || !/^\s*\.catch\(\(\)\s*=>\s*undefined\)/.test(suffix)) {
          violations.push(location)
        }
      }
    }

    // Keep this guard meaningful without coupling it to optional business pages.
    expect(callSites.length).toBeGreaterThan(0)
    expect(violations).toEqual([])
  })
})
