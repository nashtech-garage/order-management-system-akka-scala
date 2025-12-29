import { Component, input } from '@angular/core';

@Component({
  selector: 'app-loader',
  standalone: true,
  templateUrl: './loader.html',
  styleUrl: './loader.scss',
})
export class Loader {
  size = input<number>(40);
  message = input<string>('');
  fullscreen = input<boolean>(false);
}
