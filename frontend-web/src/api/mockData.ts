
export const mockVehicles = [
  { id: 1, marque: 'Dacia Logan', category: 'Economy', plate: '1-A-1234', statut: 'AVAILABLE', prixJour: 300, transmission: 'Manual', fuel: 'Diesel', image: 'https://images.unsplash.com/photo-1541899481282-d53bffe3c35d?auto=format&fit=crop&q=80&w=300' },
  { id: 2, marque: 'Renault Clio 5', category: 'Compact', plate: '6-B-5678', statut: 'RENTED', prixJour: 400, transmission: 'Manual', fuel: 'Diesel', image: 'https://images.unsplash.com/photo-1590362891991-f776e747a588?auto=format&fit=crop&q=80&w=300' },
  { id: 3, marque: 'Range Rover Velar', category: 'Luxury SUV', plate: '1-H-9999', statut: 'AVAILABLE', prixJour: 1500, transmission: 'Automatic', fuel: 'Diesel', image: 'https://images.unsplash.com/photo-1606611013016-969c19ba27bb?auto=format&fit=crop&q=80&w=300' },
  { id: 4, marque: 'Volkswagen Touareg', category: 'SUV', plate: '33-D-4321', statut: 'RENTED', prixJour: 1200, transmission: 'Automatic', fuel: 'Diesel', image: 'https://images.unsplash.com/photo-1606664515524-ed2f786a0bd6?auto=format&fit=crop&q=80&w=300' },
  { id: 5, marque: 'Mercedes Classe E', category: 'Premium', plate: '1-W-7777', statut: 'MAINTENANCE', prixJour: 1800, transmission: 'Automatic', fuel: 'Petrol', image: 'https://images.unsplash.com/photo-1618843479313-40f8afb4b4d8?auto=format&fit=crop&q=80&w=300' },
];

export const mockReservations = [
  { id: 1, clientNom: 'Youssef El Mansouri', vehicleMarque: 'Range Rover Velar', dateDebut: '2026-05-10', dateFin: '2026-05-15', prixTotal: 7500, statut: 'CONFIRMED' },
  { id: 2, clientNom: 'Amine Bennani', vehicleMarque: 'Volkswagen Touareg', dateDebut: '2026-05-05', dateFin: '2026-05-08', prixTotal: 3600, statut: 'RENTED' },
  { id: 3, clientNom: 'Laila Tazi', vehicleMarque: 'Dacia Logan', dateDebut: '2026-05-20', dateFin: '2026-05-22', prixTotal: 600, statut: 'PENDING' },
  { id: 4, clientNom: 'Mehdi Alami', vehicleMarque: 'Renault Clio 5', dateDebut: '2026-05-12', dateFin: '2026-05-15', prixTotal: 1200, statut: 'CANCELLED' },
];

export const mockStats = {
  totalVehicles: 54,
  activeReservations: 12,
  revenueDH: 458200,
  activeClients: 128,
  growth: 15.8,
};

export const revenueData = [
  { name: 'Jan', revenue: 45000 },
  { name: 'Feb', revenue: 52000 },
  { name: 'Mar', revenue: 48000 },
  { name: 'Apr', revenue: 61000 },
  { name: 'May', revenue: 55000 },
  { name: 'Jun', revenue: 67000 },
];

export const reservationData = [
  { name: 'Mon', count: 12 },
  { name: 'Tue', count: 18 },
  { name: 'Wed', count: 15 },
  { name: 'Thu', count: 22 },
  { name: 'Fri', count: 30 },
  { name: 'Sat', count: 25 },
  { name: 'Sun', count: 14 },
];

export const distributionData = [
  { name: 'Economy', value: 45 },
  { name: 'SUV', value: 25 },
  { name: 'Luxury', value: 15 },
  { name: 'Compact', value: 15 },
];
