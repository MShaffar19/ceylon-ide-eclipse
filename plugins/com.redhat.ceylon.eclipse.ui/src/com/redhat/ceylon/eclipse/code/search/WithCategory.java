package com.redhat.ceylon.eclipse.code.search;

import com.redhat.ceylon.eclipse.code.search.CeylonSearchMatch.Type;

class WithCategory {
    private Object item;
    private CeylonSearchMatch.Type category;
    Object getItem() {
        return item;
    }
    CeylonSearchMatch.Type getCategory() {
        return category;
    }
    WithCategory(Object item, Type category) {
        this.item = item;
        this.category = category;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((category == null) ? 0 : category.hashCode());
        result = prime * result + ((item == null) ? 0 : item.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        WithCategory other = (WithCategory) obj;
        if (category != other.category)
            return false;
        if (item == null) {
            if (other.item != null)
                return false;
        } else if (!item.equals(other.item))
            return false;
        return true;
    }
    @Override
    public String toString() {
        return item.toString();
    }
}