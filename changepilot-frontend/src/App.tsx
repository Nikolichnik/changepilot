import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { ChangePage } from './pages/ChangePage';
import { ListPage } from './pages/ListPage';
import { NotFoundPage } from './pages/NotFoundPage';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<ListPage />} />
        <Route path="/changes/new" element={<ChangePage />} />
        <Route path="/changes/:id" element={<ChangePage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}
