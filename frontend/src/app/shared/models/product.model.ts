export interface Product {
  id: number;
  name: string;
  description?: string;
  price: number;
  stockQuantity: number;
  categoryId?: number;
  categoryName?: string;
  imageUrl?: string;
  createdAt: string;
}

export interface Category {
  id?: number;
  name: string;
  description?: string;
}

export interface CreateProductRequest {
  name: string;
  description?: string;
  price: number;
  stockQuantity: number;
  categoryId?: number;
  imageUrl?: string;
}

export interface UpdateProductRequest {
  name?: string;
  description?: string;
  price?: number;
  stockQuantity?: number;
  categoryId?: number;
  imageUrl?: string;
}

export interface CreateCategoryRequest {
  name: string;
  description?: string;
}

export interface ProductResponse {
  id: number;
  name: string;
  description?: string;
  price: number;
  stockQuantity: number;
  categoryId?: number;
  categoryName?: string;
  imageUrl?: string;
  createdAt: string;
}
