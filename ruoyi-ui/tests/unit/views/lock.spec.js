import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('lock screen error rendering', () => {
  it('routes unlock failures through the safe error resolver', () => {
    const sourcePath = resolve(process.cwd(), 'src/views/lock.vue')
    const source = readFileSync(sourcePath, 'utf8')

    expect(source).toContain("import { resolveLabErrorMessage } from '@/utils/lab/errorMessage'")
    expect(source).toContain('showError(resolveLabErrorMessage(err))')
    expect(source).not.toMatch(/err\.(?:message|toString)/)
  })
})
