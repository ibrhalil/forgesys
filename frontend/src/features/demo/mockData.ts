/** Mock data for UI component demo pages. No API calls — all static. */

export interface MockUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  username: string;
  role: 'admin' | 'editor' | 'viewer';
  status: 'active' | 'inactive' | 'locked';
  emailVerified: boolean;
  createdAt: string;
  roleCount: number;
  groupCount: number;
}

export interface MockProduct {
  id: string;
  name: string;
  category: string;
  price: number;
  stock: number;
  active: boolean;
  sku: string;
  updatedAt: string;
}

export const MOCK_USERS: MockUser[] = [
  { id: '1', email: 'alice@example.com', firstName: 'Alice', lastName: 'Johnson', username: 'alice.j', role: 'admin', status: 'active', emailVerified: true, createdAt: '2025-01-10', roleCount: 3, groupCount: 2 },
  { id: '2', email: 'bob@example.com', firstName: 'Bob', lastName: 'Smith', username: 'bob.smith', role: 'editor', status: 'active', emailVerified: true, createdAt: '2025-02-14', roleCount: 1, groupCount: 1 },
  { id: '3', email: 'carol@example.com', firstName: 'Carol', lastName: 'Williams', username: 'carol.w', role: 'viewer', status: 'inactive', emailVerified: false, createdAt: '2025-03-05', roleCount: 1, groupCount: 0 },
  { id: '4', email: 'dave@example.com', firstName: 'Dave', lastName: 'Brown', username: 'dave.b', role: 'editor', status: 'locked', emailVerified: true, createdAt: '2025-03-22', roleCount: 2, groupCount: 1 },
  { id: '5', email: 'eve@example.com', firstName: 'Eve', lastName: 'Davis', username: 'eve.d', role: 'viewer', status: 'active', emailVerified: true, createdAt: '2025-04-01', roleCount: 1, groupCount: 3 },
  { id: '6', email: 'frank@example.com', firstName: 'Frank', lastName: 'Miller', username: 'frank.m', role: 'admin', status: 'active', emailVerified: false, createdAt: '2025-04-11', roleCount: 4, groupCount: 2 },
  { id: '7', email: 'grace@example.com', firstName: 'Grace', lastName: 'Wilson', username: 'grace.w', role: 'editor', status: 'active', emailVerified: true, createdAt: '2025-05-07', roleCount: 1, groupCount: 1 },
  { id: '8', email: 'henry@example.com', firstName: 'Henry', lastName: 'Moore', username: 'henry.m', role: 'viewer', status: 'active', emailVerified: true, createdAt: '2025-05-19', roleCount: 0, groupCount: 0 },
  { id: '9', email: 'ivy@example.com', firstName: 'Ivy', lastName: 'Taylor', username: 'ivy.t', role: 'viewer', status: 'inactive', emailVerified: false, createdAt: '2025-06-03', roleCount: 1, groupCount: 0 },
  { id: '10', email: 'jack@example.com', firstName: 'Jack', lastName: 'Anderson', username: 'jack.a', role: 'editor', status: 'active', emailVerified: true, createdAt: '2025-06-14', roleCount: 2, groupCount: 1 },
  { id: '11', email: 'kate@example.com', firstName: 'Kate', lastName: 'Thomas', username: 'kate.t', role: 'admin', status: 'active', emailVerified: true, createdAt: '2025-07-01', roleCount: 3, groupCount: 2 },
  { id: '12', email: 'liam@example.com', firstName: 'Liam', lastName: 'Jackson', username: 'liam.j', role: 'viewer', status: 'locked', emailVerified: true, createdAt: '2025-07-15', roleCount: 1, groupCount: 0 },
  { id: '13', email: 'mia@example.com', firstName: 'Mia', lastName: 'White', username: 'mia.w', role: 'editor', status: 'active', emailVerified: true, createdAt: '2025-07-28', roleCount: 1, groupCount: 1 },
  { id: '14', email: 'noah@example.com', firstName: 'Noah', lastName: 'Harris', username: 'noah.h', role: 'viewer', status: 'active', emailVerified: false, createdAt: '2025-08-05', roleCount: 1, groupCount: 0 },
  { id: '15', email: 'olivia@example.com', firstName: 'Olivia', lastName: 'Martin', username: 'olivia.m', role: 'editor', status: 'active', emailVerified: true, createdAt: '2025-08-10', roleCount: 2, groupCount: 2 },
];

export const MOCK_PRODUCTS: MockProduct[] = [
  { id: 'p1', name: 'Wireless Keyboard', category: 'Electronics', price: 79.99, stock: 142, active: true, sku: 'WK-001', updatedAt: '2025-08-01' },
  { id: 'p2', name: 'USB-C Hub', category: 'Electronics', price: 49.99, stock: 230, active: true, sku: 'UCH-004', updatedAt: '2025-08-03' },
  { id: 'p3', name: 'Ergonomic Mouse', category: 'Electronics', price: 59.99, stock: 88, active: true, sku: 'EM-007', updatedAt: '2025-08-05' },
  { id: 'p4', name: 'Monitor Stand', category: 'Accessories', price: 39.99, stock: 0, active: false, sku: 'MS-012', updatedAt: '2025-07-20' },
  { id: 'p5', name: 'Desk Lamp', category: 'Accessories', price: 29.99, stock: 65, active: true, sku: 'DL-003', updatedAt: '2025-08-10' },
  { id: 'p6', name: 'Laptop Stand', category: 'Accessories', price: 44.99, stock: 37, active: true, sku: 'LS-009', updatedAt: '2025-08-12' },
  { id: 'p7', name: 'Webcam HD', category: 'Electronics', price: 89.99, stock: 19, active: true, sku: 'WC-002', updatedAt: '2025-08-14' },
  { id: 'p8', name: 'Headset Pro', category: 'Electronics', price: 129.99, stock: 0, active: false, sku: 'HP-011', updatedAt: '2025-07-15' },
  { id: 'p9', name: 'Cable Organizer', category: 'Accessories', price: 14.99, stock: 310, active: true, sku: 'CO-006', updatedAt: '2025-08-18' },
  { id: 'p10', name: 'Mousepad XL', category: 'Accessories', price: 19.99, stock: 95, active: true, sku: 'MXL-005', updatedAt: '2025-08-20' },
];

/** Paginate an array client-side — mimics server PageResponse shape. */
export function paginate<T>(
  items: T[],
  page: number,
  size: number,
): { items: T[]; page: number; size: number; totalElements: number; totalPages: number } {
  const totalElements = items.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  const safePage = Math.min(page, totalPages - 1);
  const start = safePage * size;
  return {
    items: items.slice(start, start + size),
    page: safePage,
    size,
    totalElements,
    totalPages,
  };
}

/** Sort an array by a string/number field — mimics server-side sort. */
export function sortBy<T>(
  items: T[],
  field: keyof T,
  direction: 'asc' | 'desc',
): T[] {
  return [...items].sort((a, b) => {
    const av = a[field];
    const bv = b[field];
    const cmp =
      typeof av === 'number' && typeof bv === 'number'
        ? av - bv
        : String(av ?? '').localeCompare(String(bv ?? ''));
    return direction === 'asc' ? cmp : -cmp;
  });
}
