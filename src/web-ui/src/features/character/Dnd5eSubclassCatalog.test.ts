import { describe, expect, it } from 'vitest'
import { subclassesFor } from './Dnd5eSubclassCatalog'

describe('Dnd5eSubclassCatalog', () => {
  it('provides level-one subclass choices only for classes that choose at level one', () => {
    expect(subclassesFor('클레릭').length).toBeGreaterThan(0)
    expect(subclassesFor('소서러').length).toBeGreaterThan(0)
    expect(subclassesFor('워락').length).toBeGreaterThan(0)
    expect(subclassesFor('파이터')).toEqual([])
    expect(subclassesFor('로그')).toEqual([])
  })

  it('includes descriptions and automatic features for every option', () => {
    for (const characterClass of ['클레릭', '소서러', '워락']) {
      for (const option of subclassesFor(characterClass)) {
        expect(option.description.length).toBeGreaterThan(0)
        expect(option.features.length).toBeGreaterThan(0)
      }
    }
  })
})
