import { describe, expect, it } from 'vitest'
import { subclassesFor } from './Dnd5eSubclassCatalog'

describe('Dnd5eSubclassCatalog', () => {
  it('only exposes subclass choices present in dnd5th.pdf for each class', () => {
    expect(subclassesFor('클레릭').map(option => option.id)).toEqual(['생명 권역'])
    expect(subclassesFor('파이터').map(option => option.id)).toEqual(['챔피언'])
    expect(subclassesFor('로그').map(option => option.id)).toEqual(['시프'])
    expect(subclassesFor('위저드').map(option => option.id)).toEqual([
      '방출학파', '방호학파', '변환학파', '사령학파', '예지학파', '조형학파', '환영학파', '환혹학파',
    ])
    expect(subclassesFor('소서러')).toEqual([])
    expect(subclassesFor('워락')).toEqual([])
  })

  it('uses rulebook-grounded descriptions and automatic features for every detailed option', () => {
    for (const characterClass of ['클레릭', '파이터', '로그', '위저드']) {
      for (const option of subclassesFor(characterClass)) {
        expect(option.description.length).toBeGreaterThan(0)
        if (characterClass !== '위저드' || option.id === '방출학파') expect(option.features.length).toBeGreaterThan(0)
      }
    }
    expect(subclassesFor('클레릭')[0].description).toContain('생명과 건강')
    expect(subclassesFor('로그')[0].features).toContain('빠른 손놀림')
    expect(subclassesFor('파이터')[0].features).toContain('향상된 치명타')
  })
})
