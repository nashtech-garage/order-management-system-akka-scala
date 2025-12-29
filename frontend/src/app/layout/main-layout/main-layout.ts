import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Header } from '@layout/header/header';
import { Footer } from '@layout/footer/footer';
import { Sidebar } from '@layout/sidebar/sidebar';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, Header, Footer, Sidebar],
  templateUrl: './main-layout.html',
  styleUrl: './main-layout.scss',
})
export class MainLayout {}
