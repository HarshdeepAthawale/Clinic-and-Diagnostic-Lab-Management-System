import { InventoryView } from '@/components/inventory/InventoryView';

export const metadata = { title: 'Inventory' };

export default function AdminInventoryPage() {
  return <InventoryView canManage />;
}
